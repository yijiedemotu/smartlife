package com.smartlife.controller;

import com.smartlife.common.Result;
import com.smartlife.security.RoleGuard;
import com.smartlife.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端：平台经营看板
 *
 * 数据口径为"全平台"，与商家端 /merchant/dashboard/**（单店口径）形成对照。
 */
@RestController
@RequestMapping("/admin/stats")
public class AdminStatsController {

    private final StatsService statsService;

    public AdminStatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    /** 平台总览：GMV/订单/用户/商家/店铺/待审核/UV/在线数 */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        RoleGuard.requireAdmin();
        return Result.ok(statsService.platformOverview());
    }

    /** 平台近 N 天订单/GMV 趋势 */
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "7") Integer days) {
        RoleGuard.requireAdmin();
        return Result.ok(statsService.platformTrend(days == null ? 7 : days));
    }

    /** 平台近 N 天新增账号趋势 */
    @GetMapping("/userTrend")
    public Result<List<Map<String, Object>>> userTrend(@RequestParam(defaultValue = "7") Integer days) {
        RoleGuard.requireAdmin();
        return Result.ok(statsService.userTrend(days == null ? 7 : days));
    }

    /** 店铺审核状态分布（治理进度） */
    @GetMapping("/shopAudit")
    public Result<List<Map<String, Object>>> shopAudit() {
        RoleGuard.requireAdmin();
        return Result.ok(statsService.shopAuditDistribution());
    }

    /** 账号角色分布 */
    @GetMapping("/roleDistribution")
    public Result<List<Map<String, Object>>> roleDistribution() {
        RoleGuard.requireAdmin();
        return Result.ok(statsService.roleDistribution());
    }

    /** 订单状态分布 */
    @GetMapping("/orderStatus")
    public Result<List<Map<String, Object>>> orderStatus() {
        RoleGuard.requireAdmin();
        return Result.ok(statsService.orderStatusDistribution());
    }

    /** 店铺 GMV 排行 TopN */
    @GetMapping("/shopRank")
    public Result<List<Map<String, Object>>> shopRank() {
        RoleGuard.requireAdmin();
        return Result.ok(statsService.shopRank());
    }
}
