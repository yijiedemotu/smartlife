package com.smartlife.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartlife.common.RoleConstants;
import com.smartlife.entity.Shop;
import com.smartlife.entity.User;
import com.smartlife.mapper.MerchantApplyMapper;
import com.smartlife.mapper.OrdersMapper;
import com.smartlife.mapper.ShopMapper;
import com.smartlife.mapper.UserMapper;
import com.smartlife.ws.WsSessionManager;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 看板统计服务（管理端平台视角 / 商家端店铺视角共用底层聚合）
 */
@Service
public class StatsService {

    private final OrdersMapper ordersMapper;
    private final UserMapper userMapper;
    private final ShopMapper shopMapper;
    private final MerchantApplyMapper applyMapper;
    private final UvService uvService;
    private final WsSessionManager wsSessionManager;

    public StatsService(OrdersMapper ordersMapper, UserMapper userMapper, ShopMapper shopMapper,
                        MerchantApplyMapper applyMapper, UvService uvService,
                        WsSessionManager wsSessionManager) {
        this.ordersMapper = ordersMapper;
        this.userMapper = userMapper;
        this.shopMapper = shopMapper;
        this.applyMapper = applyMapper;
        this.uvService = uvService;
        this.wsSessionManager = wsSessionManager;
    }

    // ==================== 管理端：平台总览 ====================

    public Map<String, Object> platformOverview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Map<String, Object> map = new HashMap<>();
        map.put("todayOrders", ordersMapper.countOrders(null, todayStart, null));
        map.put("todayGmv", ordersMapper.sumGmv(null, todayStart));
        map.put("totalOrders", ordersMapper.countOrders(null, null, null));
        map.put("totalGmv", ordersMapper.sumGmv(null, null));
        map.put("totalUsers", countUser(RoleConstants.ROLE_USER));
        map.put("totalMerchants", countUser(RoleConstants.ROLE_MERCHANT));
        map.put("totalShops", countShop(null));
        map.put("openShops", countShop(RoleConstants.SHOP_AUDIT_APPROVED));
        map.put("pendingShops", countShop(RoleConstants.SHOP_AUDIT_PENDING));
        map.put("pendingApplies", countPendingApply());
        map.put("bannedUsers", userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getStatus, RoleConstants.USER_STATUS_BANNED)));
        map.put("uvToday", uvService.sumToday());
        map.put("onlineUsers", wsSessionManager.onlineUserCount());
        return map;
    }

    /** 平台近 N 天订单/GMV 趋势 */
    public List<Map<String, Object>> platformTrend(int days) {
        int d = clampDays(days);
        return fillTrend(ordersMapper.selectDailyTrend(LocalDate.now().minusDays(d - 1L).atStartOfDay()), d, true);
    }

    /** 平台近 N 天新增账号趋势（全角色合计） */
    public List<Map<String, Object>> userTrend(int days) {
        int d = clampDays(days);
        LocalDateTime begin = LocalDate.now().minusDays(d - 1L).atStartOfDay();
        return fillTrend(userMapper.selectRegisterTrend(begin), d, false);
    }

    /** 平台店铺审核状态分布 */
    public List<Map<String, Object>> shopAuditDistribution() {
        List<Map<String, Object>> raw = shopMapper.selectAuditDistribution();
        Map<Integer, Long> byStatus = new HashMap<>();
        for (Map<String, Object> row : raw) {
            byStatus.put(((Number) row.get("auditStatus")).intValue(), toLong(row.get("cnt")));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        int[] order = {RoleConstants.SHOP_AUDIT_PENDING, RoleConstants.SHOP_AUDIT_APPROVED,
                RoleConstants.SHOP_AUDIT_REJECTED, RoleConstants.SHOP_AUDIT_CLOSED};
        for (int status : order) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("status", status);
            item.put("name", RoleConstants.shopAuditText(status));
            item.put("value", byStatus.getOrDefault(status, 0L));
            result.add(item);
        }
        return result;
    }

    /** 平台角色分布（用户/商家/管理员） */
    public List<Map<String, Object>> roleDistribution() {
        Map<Integer, Long> byRole = new HashMap<>();
        for (Map<String, Object> row : userMapper.selectRoleDistribution()) {
            byRole.put(((Number) row.get("role")).intValue(), toLong(row.get("cnt")));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(roleItem("用户", RoleConstants.ROLE_USER, byRole));
        result.add(roleItem("商家", RoleConstants.ROLE_MERCHANT, byRole));
        result.add(roleItem("管理员", RoleConstants.ROLE_ADMIN, byRole));
        return result;
    }

    private Map<String, Object> roleItem(String name, int role, Map<Integer, Long> byRole) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("role", role);
        item.put("name", name);
        item.put("value", byRole.getOrDefault(role, 0L));
        return item;
    }

    /** 平台订单状态分布 */
    public List<Map<String, Object>> orderStatusDistribution() {
        List<Map<String, Object>> raw = ordersMapper.selectStatusDistribution(null);
        Map<Integer, Long> byStatus = new HashMap<>();
        for (Map<String, Object> row : raw) {
            byStatus.put(((Number) row.get("status")).intValue(), toLong(row.get("cnt")));
        }
        String[] names = {"", "待支付", "待接单", "已接单", "配送中", "已完成", "已取消"};
        List<Map<String, Object>> result = new ArrayList<>();
        for (int status = 1; status <= 6; status++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("status", status);
            item.put("name", names[status]);
            item.put("value", byStatus.getOrDefault(status, 0L));
            result.add(item);
        }
        return result;
    }

    /** 平台店铺 GMV 排行 */
    public List<Map<String, Object>> shopRank() {
        return shopMapper.selectShopRank(10);
    }

    // ==================== 商家端：店铺经营看板 ====================

    /** 商家端总览：订单/GMV/待处理 + 券核销概览由 controller 追加 */
    public Map<String, Object> merchantOverview(Long shopId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Map<String, Object> map = new HashMap<>();
        map.put("todayOrders", ordersMapper.countOrders(shopId, todayStart, null));
        map.put("todayGmv", ordersMapper.sumGmv(shopId, todayStart));
        map.put("totalOrders", ordersMapper.countOrders(shopId, null, null));
        map.put("totalGmv", ordersMapper.sumGmv(shopId, null));
        map.put("pendingOrders", ordersMapper.countOrders(shopId, null, 2));
        map.put("deliveringOrders", ordersMapper.countOrders(shopId, null, 4));
        return map;
    }

    /** 商家端近 N 天订单/GMV 趋势 */
    public List<Map<String, Object>> merchantTrend(Long shopId, int days) {
        int d = clampDays(days);
        return fillTrend(ordersMapper.selectDailyTrendByShop(shopId,
                LocalDate.now().minusDays(d - 1L).atStartOfDay()), d, true);
    }

    /** 商家端订单状态分布 */
    public List<Map<String, Object>> merchantOrderStatus(Long shopId) {
        List<Map<String, Object>> raw = ordersMapper.selectStatusDistribution(shopId);
        Map<Integer, Long> byStatus = new HashMap<>();
        for (Map<String, Object> row : raw) {
            byStatus.put(((Number) row.get("status")).intValue(), toLong(row.get("cnt")));
        }
        String[] names = {"", "待支付", "待接单", "已接单", "配送中", "已完成", "已取消"};
        List<Map<String, Object>> result = new ArrayList<>();
        for (int status = 1; status <= 6; status++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("status", status);
            item.put("name", names[status]);
            item.put("value", byStatus.getOrDefault(status, 0L));
            result.add(item);
        }
        return result;
    }

    /** 商家端热销商品 TopN（按销量） */
    public List<Map<String, Object>> merchantTopProducts(Long shopId) {
        return ordersMapper.selectTopProducts(shopId, 5);
    }

    // ==================== 内部工具 ====================

    private long countUser(int role) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, role));
        return count == null ? 0 : count;
    }

    private long countShop(Integer auditStatus) {
        Long count = shopMapper.selectCount(new LambdaQueryWrapper<Shop>()
                .eq(auditStatus != null, Shop::getAuditStatus, auditStatus));
        return count == null ? 0 : count;
    }

    private long countPendingApply() {
        Long count = applyMapper.selectCount(new LambdaQueryWrapper<com.smartlife.entity.MerchantApply>()
                .eq(com.smartlife.entity.MerchantApply::getStatus, RoleConstants.APPLY_PENDING));
        return count == null ? 0 : count;
    }

    private int clampDays(int days) {
        return Math.max(2, Math.min(days, 30));
    }

    /**
     * 把按天聚合结果补齐为连续日期序列
     * withGmv=true 时输出 orders/gmv（订单趋势），false 时输出 count（注册趋势）
     */
    private List<Map<String, Object>> fillTrend(List<Map<String, Object>> raw, int days, boolean withGmv) {
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
            if (withGmv) {
                item.put("orders", row == null ? 0 : toLong(row.get("cnt")));
                item.put("gmv", row == null ? 0 : toLong(row.get("gmv")));
            } else {
                item.put("count", row == null ? 0 : toLong(row.get("cnt")));
            }
            result.add(item);
        }
        return result;
    }

    private long toLong(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof java.math.BigDecimal bd) {
            return bd.longValue();
        }
        return ((Number) o).longValue();
    }
}
