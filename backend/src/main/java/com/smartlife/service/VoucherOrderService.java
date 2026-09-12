package com.smartlife.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartlife.common.BusinessException;
import com.smartlife.common.RedisConstants;
import com.smartlife.entity.User;
import com.smartlife.entity.Voucher;
import com.smartlife.entity.VoucherOrder;
import com.smartlife.mapper.ShopMapper;
import com.smartlife.mapper.UserMapper;
import com.smartlife.mapper.VoucherMapper;
import com.smartlife.mapper.VoucherOrderMapper;
import com.smartlife.vo.VoucherOrderVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 券订单服务：负责 DB 层下单事务（秒杀消费者与普通领取共用）
 */
@Service
public class VoucherOrderService {

    private final VoucherOrderMapper voucherOrderMapper;
    private final VoucherMapper voucherMapper;
    private final ShopMapper shopMapper;
    private final UserMapper userMapper;

    public VoucherOrderService(VoucherOrderMapper voucherOrderMapper, VoucherMapper voucherMapper,
                               ShopMapper shopMapper, UserMapper userMapper) {
        this.voucherOrderMapper = voucherOrderMapper;
        this.voucherMapper = voucherMapper;
        this.shopMapper = shopMapper;
        this.userMapper = userMapper;
    }

    /**
     * 创建券订单（事务）：
     * 1) 校验券状态与秒杀时间窗
     * 2) DB 乐观条件更新库存 stock-1（where stock>0），兜底防超卖
     * 3) 插入订单（user_id+voucher_id 唯一索引 = 一人一单最终防线）
     * 任一失败整体回滚
     */
    @Transactional(rollbackFor = Exception.class)
    public VoucherOrder createVoucherOrderTx(Long userId, Long voucherId) {
        Voucher voucher = voucherMapper.selectById(voucherId);
        if (voucher == null || voucher.getStatus() != 1) {
            throw new BusinessException("优惠券不存在或已下架");
        }
        if (voucher.getAuditStatus() != null && voucher.getAuditStatus() != 1) {
            throw new BusinessException("该券已被平台下架");
        }
        if (voucher.getType() == Voucher.TYPE_SECOND_KILL) {
            LocalDateTime now = LocalDateTime.now();
            if (voucher.getBeginTime() != null && now.isBefore(voucher.getBeginTime())) {
                throw new BusinessException("秒杀尚未开始");
            }
            if (voucher.getEndTime() != null && now.isAfter(voucher.getEndTime())) {
                throw new BusinessException("秒杀已结束");
            }
        }
        int rows = voucherMapper.cutStock(voucherId);
        if (rows == 0) {
            throw new BusinessException(voucher.getType() == Voucher.TYPE_SECOND_KILL
                    ? "手慢了，已被抢光" : "优惠券已领完");
        }
        VoucherOrder order = new VoucherOrder();
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        order.setShopId(voucher.getShopId());
        order.setVoucherTitle(voucher.getTitle());
        order.setActualValue(voucher.getActualValue());
        order.setStatus(VoucherOrder.STATUS_UNUSED);
        voucherOrderMapper.insert(order);
        return order;
    }

    /** 我的券分页 */
    public List<VoucherOrderVO> myPage(Long userId, Integer page, Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int sz = size == null || size < 1 ? 10 : Math.min(size, 50);
        List<VoucherOrder> orders = voucherOrderMapper.selectList(
                new LambdaQueryWrapper<VoucherOrder>()
                        .eq(VoucherOrder::getUserId, userId)
                        .orderByDesc(VoucherOrder::getCreateTime)
                        .last("LIMIT " + (p - 1) * sz + "," + sz));
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<Long, String> shopNames = shopMapper.selectBatchIds(
                        orders.stream().map(VoucherOrder::getShopId).distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(s -> s.getId(), s -> s.getName()));
        return orders.stream().map(o -> {
            VoucherOrderVO vo = new VoucherOrderVO();
            vo.setId(o.getId());
            vo.setVoucherId(o.getVoucherId());
            vo.setShopId(o.getShopId());
            vo.setVoucherTitle(o.getVoucherTitle());
            vo.setActualValue(o.getActualValue());
            vo.setStatus(o.getStatus());
            vo.setShopName(shopNames.get(o.getShopId()));
            vo.setCreateTime(o.getCreateTime());
            vo.setUseTime(o.getUseTime());
            return vo;
        }).collect(Collectors.toList());
    }

    /** 核销优惠券（演示：到店使用） */
    public void useVoucher(Long orderId, Long userId) {
        VoucherOrder order = voucherOrderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("券不存在");
        }
        if (order.getStatus() != VoucherOrder.STATUS_UNUSED) {
            throw new BusinessException("该券已被使用");
        }
        order.setStatus(VoucherOrder.STATUS_USED);
        order.setUseTime(LocalDateTime.now());
        voucherOrderMapper.updateById(order);
    }

    // ==================== 商家端：券核销 ====================

    /**
     * 商家端券订单分页：只看自己店铺卖出的券（领券人昵称一并返回，便于到店核销对账）
     */
    public Map<String, Object> merchantPage(Long shopId, Integer status, Integer page, Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int sz = size == null || size < 1 ? 10 : Math.min(size, 50);
        LambdaQueryWrapper<VoucherOrder> wrapper = new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getShopId, shopId)
                .eq(status != null && status > 0, VoucherOrder::getStatus, status)
                .orderByDesc(VoucherOrder::getCreateTime);
        Long total = voucherOrderMapper.selectCount(wrapper);
        List<VoucherOrder> orders = voucherOrderMapper.selectList(
                wrapper.last("LIMIT " + (p - 1) * sz + "," + sz));
        Map<String, Object> map = new HashMap<>();
        map.put("total", total == null ? 0 : total);
        map.put("records", toMerchantVOs(orders));
        return map;
    }

    /** 商家端核销：校验券属于自己店铺 */
    public void merchantUse(Long shopId, Long orderId) {
        VoucherOrder order = voucherOrderMapper.selectById(orderId);
        if (order == null || !shopId.equals(order.getShopId())) {
            throw new BusinessException("该券不属于您的店铺");
        }
        if (order.getStatus() != VoucherOrder.STATUS_UNUSED) {
            throw new BusinessException("该券已被使用或已过期");
        }
        order.setStatus(VoucherOrder.STATUS_USED);
        order.setUseTime(LocalDateTime.now());
        voucherOrderMapper.updateById(order);
    }

    /** 商家端核销统计：已发放/已核销/待核销 */
    public Map<String, Object> merchantStats(Long shopId) {
        Map<String, Object> map = new HashMap<>();
        map.put("total", countByShop(shopId, null));
        map.put("used", countByShop(shopId, VoucherOrder.STATUS_USED));
        map.put("unused", countByShop(shopId, VoucherOrder.STATUS_UNUSED));
        return map;
    }

    private long countByShop(Long shopId, Integer status) {
        Long count = voucherOrderMapper.selectCount(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getShopId, shopId)
                .eq(status != null, VoucherOrder::getStatus, status));
        return count == null ? 0 : count;
    }

    private List<VoucherOrderVO> toMerchantVOs(List<VoucherOrder> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<Long, String> userNames = userMapper.selectBatchIds(
                        orders.stream().map(VoucherOrder::getUserId).distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(u -> u.getId(), u -> u.getNickname() == null ? "" : u.getNickname()));
        return orders.stream().map(o -> {
            VoucherOrderVO vo = new VoucherOrderVO();
            vo.setId(o.getId());
            vo.setVoucherId(o.getVoucherId());
            vo.setShopId(o.getShopId());
            vo.setVoucherTitle(o.getVoucherTitle());
            vo.setActualValue(o.getActualValue());
            vo.setStatus(o.getStatus());
            vo.setUserName(userNames.get(o.getUserId()));
            vo.setCreateTime(o.getCreateTime());
            vo.setUseTime(o.getUseTime());
            return vo;
        }).collect(Collectors.toList());
    }

    public long countByUserAndVoucher(Long userId, Long voucherId) {
        Long count = voucherOrderMapper.selectCount(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getVoucherId, voucherId));
        return count == null ? 0 : count;
    }
}
