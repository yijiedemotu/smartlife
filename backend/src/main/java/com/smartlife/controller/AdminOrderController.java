package com.smartlife.controller;

import com.smartlife.common.Result;
import com.smartlife.security.RoleGuard;
import com.smartlife.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端：全平台订单监控与代管处置
 *
 * 日常接单/配送由商家在 /merchant/order/** 完成；
 * 平台侧用于全平台订单检索、异常订单兜底处理（客服介入）。
 * 传入 merchantId=null 表示不限制归属（平台代管）。
 */
@RestController
@RequestMapping("/admin/order")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 全平台订单分页（shopId 可选） */
    @GetMapping("/page")
    public Result<Map<String, Object>> page(@RequestParam(required = false) Integer status,
                                            @RequestParam(required = false) Long shopId,
                                            @RequestParam(defaultValue = "1") Integer page,
                                            @RequestParam(defaultValue = "10") Integer size) {
        RoleGuard.requireAdmin();
        return Result.ok(orderService.adminPage(shopId, status, page, size));
    }

    @PostMapping("/{orderId}/accept")
    public Result<Void> accept(@PathVariable Long orderId) {
        RoleGuard.requireAdmin();
        orderService.accept(orderId, null);
        return Result.ok();
    }

    @PostMapping("/{orderId}/deliver")
    public Result<Void> deliver(@PathVariable Long orderId) {
        RoleGuard.requireAdmin();
        orderService.deliver(orderId, null);
        return Result.ok();
    }

    @PostMapping("/{orderId}/finish")
    public Result<Void> finish(@PathVariable Long orderId) {
        RoleGuard.requireAdmin();
        orderService.finish(orderId, null);
        return Result.ok();
    }

    /** 平台兜底取消（客服介入场景） */
    @PostMapping("/{orderId}/cancel")
    public Result<Void> cancel(@PathVariable Long orderId) {
        RoleGuard.requireAdmin();
        orderService.cancelByMerchant(orderId, null);
        return Result.ok();
    }
}
