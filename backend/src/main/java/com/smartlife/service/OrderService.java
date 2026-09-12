package com.smartlife.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartlife.common.BusinessException;
import com.smartlife.common.RedisConstants;
import com.smartlife.common.RoleConstants;
import com.smartlife.dto.OrderCreateDTO;
import com.smartlife.entity.OrderItem;
import com.smartlife.entity.Orders;
import com.smartlife.entity.Product;
import com.smartlife.entity.Shop;
import com.smartlife.entity.User;
import com.smartlife.mapper.OrderItemMapper;
import com.smartlife.mapper.OrdersMapper;
import com.smartlife.mapper.ProductMapper;
import com.smartlife.mapper.ShopMapper;
import com.smartlife.mapper.UserMapper;
import com.smartlife.vo.OrderVO;
import com.smartlife.ws.MessagePusher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 外卖订单服务（参考苍穹外卖订单履约链路）
 * 创建：库存条件扣减 -> 订单+明细快照 -> 清理购物车
 * 履约：支付 -> 商家接单/配送/完成，全程 WebSocket 实时推送
 */
@Service
public class OrderService {

    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final ShopMapper shopMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate redis;

    public OrderService(OrdersMapper ordersMapper, OrderItemMapper orderItemMapper, ProductMapper productMapper,
                        ShopMapper shopMapper, UserMapper userMapper, StringRedisTemplate redis) {
        this.ordersMapper = ordersMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.shopMapper = shopMapper;
        this.userMapper = userMapper;
        this.redis = redis;
    }

    // ==================== 用户端 ====================

    /** 下单（同一店铺）：校验库存 -> 快照 -> 扣库存 -> 清购物车 */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO create(Long userId, OrderCreateDTO dto) {
        Shop shop = shopMapper.selectById(dto.getShopId());
        if (shop == null) {
            throw new BusinessException("店铺不存在");
        }
        if (!RoleConstants.visibleToUser(shop)) {
            throw new BusinessException("该店铺当前休息中或未通过审核，暂不接单");
        }
        if (dto.getItems().isEmpty()) {
            throw new BusinessException("订单不能为空");
        }
        // 第一遍：校验商品归属与在售状态，计算金额
        List<Product> products = new ArrayList<>();
        Map<Long, Integer> counts = new HashMap<>();
        int amount = 0;
        for (int i = 0; i < dto.getItems().size(); i++) {
            var item = dto.getItems().get(i);
            Product product = productMapper.selectById(item.getProductId());
            if (product == null || product.getStatus() != 1 || !product.getShopId().equals(dto.getShopId())) {
                throw new BusinessException("部分商品无效或不属于该店铺");
            }
            products.add(product);
            counts.put(product.getId(), item.getCount());
            amount += product.getPrice() * item.getCount();
        }

        Orders order = new Orders();
        order.setNumber(genOrderNumber());
        order.setUserId(userId);
        order.setShopId(shop.getId());
        order.setShopName(shop.getName());
        order.setAddress(dto.getAddress() == null || dto.getAddress().isBlank() ? "默认收货地址" : dto.getAddress());
        order.setAmount(amount);
        order.setStatus(Orders.STATUS_PENDING_PAY);
        order.setRemark(dto.getRemark());
        ordersMapper.insert(order);

        // 第二遍：条件扣减库存（防超卖）并写明细快照，同时清理购物车对应项
        String cartKey = RedisConstants.cartKey(userId);
        for (Product product : products) {
            int count = counts.get(product.getId());
            int rows = productMapper.reduceStock(product.getId(), count);
            if (rows == 0) {
                throw new BusinessException("「" + product.getName() + "」库存不足");
            }
            OrderItem oi = new OrderItem();
            oi.setOrderId(order.getId());
            oi.setProductId(product.getId());
            oi.setProductName(product.getName());
            oi.setProductImage(product.getImages());
            oi.setPrice(product.getPrice());
            oi.setCount(count);
            orderItemMapper.insert(oi);
            redis.opsForHash().delete(cartKey, String.valueOf(product.getId()));
        }
        return toVO(order, null);
    }

    /** 模拟支付：更新状态并广播商家端（WebSocket 新订单提醒） */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO pay(Long userId, Long orderId) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != Orders.STATUS_PENDING_PAY) {
            throw new BusinessException("当前状态不可支付");
        }
        order.setStatus(Orders.STATUS_PAID);
        order.setPayTime(LocalDateTime.now());
        ordersMapper.updateById(order);

        // WebSocket 推送给该店铺的商家端：新订单提醒（苍穹外卖：商家端响铃）
        User user = userMapper.selectById(userId);
        Shop shop = shopMapper.selectById(order.getShopId());
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", order.getId());
        data.put("number", order.getNumber());
        data.put("shopId", order.getShopId());
        data.put("shopName", order.getShopName());
        data.put("merchantId", shop == null ? null : shop.getMerchantId());
        data.put("amount", order.getAmount());
        data.put("userName", user == null ? "" : user.getNickname());
        data.put("address", order.getAddress());
        data.put("remark", order.getRemark());
        MessagePusher.pusher().newOrderToMerchant(shop == null ? null : shop.getMerchantId(), data);
        return toVO(order, null);
    }

    /** 用户取消（仅待支付可取消），回补库存 */
    @Transactional(rollbackFor = Exception.class)
    public void cancelByUser(Long userId, Long orderId) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != Orders.STATUS_PENDING_PAY) {
            throw new BusinessException("订单已支付或已处理，无法取消");
        }
        cancelAndRestore(order);
    }

    /** 超时自动取消（定时任务调用）：回补库存 */
    public void autoCancel(Orders order) {
        cancelAndRestore(order);
    }

    private void cancelAndRestore(Orders order) {
        order.setStatus(Orders.STATUS_CANCELED);
        ordersMapper.updateById(order);
        // 回补已预扣库存
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, order.getId()));
        for (OrderItem item : items) {
            productMapper.addStock(item.getProductId(), item.getCount());
        }
    }

    /** 我的订单分页 */
    public Map<String, Object> myOrders(Long userId, Integer status, Integer page, Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? 10 : Math.min(size, 50);
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<Orders>()
                .eq(Orders::getUserId, userId)
                .eq(status != null && status > 0, Orders::getStatus, status)
                .orderByDesc(Orders::getCreateTime)
                .last("LIMIT " + (p - 1) * s + "," + s);
        List<Orders> orders = ordersMapper.selectList(wrapper);
        List<OrderVO> vos = assembleWithItems(orders, null);
        return pageResult(vos, count(userId, null, status));
    }

    // ==================== 商家端 / 管理端 ====================

    /**
     * 订单分页：
     *   shopId != null -> 仅该店铺（商家端，配合 MerchantService.requireShop 使用）
     *   shopId == null -> 全平台（管理端）
     */
    public Map<String, Object> adminPage(Long shopId, Integer status, Integer page, Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? 10 : Math.min(size, 50);
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<Orders>()
                .eq(shopId != null, Orders::getShopId, shopId)
                .eq(status != null && status > 0, Orders::getStatus, status)
                .orderByDesc(Orders::getCreateTime)
                .last("LIMIT " + (p - 1) * s + "," + s);
        List<Orders> orders = ordersMapper.selectList(wrapper);
        List<Long> userIds = orders.stream().map(Orders::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> users = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        List<OrderVO> vos = assembleWithItems(orders, users);
        return pageResult(vos, count(null, shopId, status));
    }

    /**
     * 商家端接单：2 -> 3
     * merchantId 为当前登录商家（管理端代管时传 null 表示不限制归属）
     */
    public void accept(Long orderId, Long merchantId) {
        transit(orderId, merchantId, Orders.STATUS_PAID, Orders.STATUS_ACCEPTED, "商家已接单，开始制作");
    }

    /** 开始配送：3 -> 4 */
    public void deliver(Long orderId, Long merchantId) {
        transit(orderId, merchantId, Orders.STATUS_ACCEPTED, Orders.STATUS_DELIVERING, "骑手已取餐，配送中");
    }

    /** 完成：4 -> 5 */
    public void finish(Long orderId, Long merchantId) {
        transit(orderId, merchantId, Orders.STATUS_DELIVERING, Orders.STATUS_FINISHED, "订单已完成，感谢惠顾");
    }

    /** 商家取消：待支付/已支付均可 */
    public void cancelByMerchant(Long orderId, Long merchantId) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        assertOwnership(order, merchantId);
        if (order.getStatus() == Orders.STATUS_PENDING_PAY || order.getStatus() == Orders.STATUS_PAID) {
            cancelAndRestore(order);
            pushStatus(order.getUserId(), order.getId(), Orders.STATUS_CANCELED, "商家已取消订单");
        } else {
            throw new BusinessException("当前状态不可取消");
        }
    }

    /**
     * 商家端订单看板：今日/累计订单量、GMV、待处理单量。
     * 说明：看板聚合口径与 StatsService.merchantOverview 保持一致，
     * 商家端接口统一走 StatsService，本方法保留给内部/测试直接调用。
     */
    public Map<String, Object> merchantOrderStats(Long shopId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Map<String, Object> map = new HashMap<>();
        map.put("todayOrders", ordersMapper.countOrders(shopId, todayStart, null));
        map.put("todayGmv", ordersMapper.sumGmv(shopId, todayStart));
        map.put("totalOrders", ordersMapper.countOrders(shopId, null, null));
        map.put("totalGmv", ordersMapper.sumGmv(shopId, null));
        map.put("pendingOrders", ordersMapper.countOrders(shopId, null, Orders.STATUS_PAID));
        map.put("deliveringOrders", ordersMapper.countOrders(shopId, null, Orders.STATUS_DELIVERING));
        return map;
    }

    /** 商家端近 N 天订单/GMV 趋势 */
    public List<Map<String, Object>> merchantTrend(Long shopId, int days) {
        return fillTrend(ordersMapper.selectDailyTrendByShop(shopId, beginOf(days)), days);
    }

    /** 归属校验：商家只能操作自己店铺的订单（merchantId=null 表示管理端不限制） */
    private void assertOwnership(Orders order, Long merchantId) {
        if (merchantId == null) {
            return;
        }
        Shop shop = shopMapper.selectById(order.getShopId());
        if (shop == null || !merchantId.equals(shop.getMerchantId())) {
            throw new BusinessException("无权操作其它店铺的订单");
        }
    }

    /** 状态机流转 + 归属校验 + WebSocket 推送用户 */
    private void transit(Long orderId, Long merchantId, int from, int to, String msg) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        assertOwnership(order, merchantId);
        if (order.getStatus() != from) {
            throw new BusinessException("订单状态已变更，请刷新");
        }
        order.setStatus(to);
        ordersMapper.updateById(order);
        pushStatus(order.getUserId(), orderId, to, msg);
    }

    private void pushStatus(Long userId, Long orderId, int status, String msg) {
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", orderId);
        data.put("status", status);
        data.put("msg", msg);
        MessagePusher.pusher().orderStatus(userId, data);
    }

    // ==================== 组装工具 ====================

    private Map<String, Object> pageResult(List<OrderVO> records, long total) {
        Map<String, Object> map = new HashMap<>();
        map.put("total", total);
        map.put("records", records);
        return map;
    }

    private long count(Long userId, Long shopId, Integer status) {
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<Orders>()
                .eq(userId != null, Orders::getUserId, userId)
                .eq(shopId != null, Orders::getShopId, shopId)
                .eq(status != null && status > 0, Orders::getStatus, status);
        Long count = ordersMapper.selectCount(wrapper);
        return count == null ? 0 : count;
    }

    /** 把稀疏的按天聚合结果补齐为连续日期序列（前端图表直接可用） */
    private List<Map<String, Object>> fillTrend(List<Map<String, Object>> raw, int days) {
        Map<String, Map<String, Object>> byDate = new HashMap<>();
        for (Map<String, Object> row : raw) {
            byDate.put(String.valueOf(row.get("stat_date")), row);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            Map<String, Object> row = byDate.get(date.toString());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date.toString());
            item.put("orders", row == null ? 0 : toLong(row.get("cnt")));
            item.put("gmv", row == null ? 0 : toLong(row.get("gmv")));
            result.add(item);
        }
        return result;
    }

    private LocalDateTime beginOf(int days) {
        int d = Math.max(2, Math.min(days, 30));
        return LocalDate.now().minusDays(d - 1L).atStartOfDay();
    }

    private long toLong(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof BigDecimal bd) {
            return bd.longValue();
        }
        return ((Number) o).longValue();
    }

    private List<OrderVO> assembleWithItems(List<Orders> orders, Map<Long, User> users) {
        if (orders.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> orderIds = orders.stream().map(Orders::getId).collect(Collectors.toList());
        Map<Long, List<OrderItem>> itemMap = orderItemMapper.selectList(
                        new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds))
                .stream().collect(Collectors.groupingBy(OrderItem::getOrderId));
        return orders.stream().map(o -> {
            OrderVO vo = toVO(o, users == null ? null : users.get(o.getUserId()));
            vo.setItems(itemMap.getOrDefault(o.getId(), new ArrayList<>()));
            return vo;
        }).collect(Collectors.toList());
    }

    private OrderVO toVO(Orders order, User user) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setNumber(order.getNumber());
        vo.setUserId(order.getUserId());
        if (user != null) {
            vo.setUserName(user.getNickname());
        }
        vo.setShopId(order.getShopId());
        vo.setShopName(order.getShopName());
        vo.setAddress(order.getAddress());
        vo.setAmount(order.getAmount());
        vo.setStatus(order.getStatus());
        vo.setRemark(order.getRemark());
        vo.setPayTime(order.getPayTime());
        vo.setCreateTime(order.getCreateTime());
        return vo;
    }

    private String genOrderNumber() {
        return "SL" + System.currentTimeMillis()
                + ThreadLocalRandom.current().nextInt(1000, 9999);
    }
}
