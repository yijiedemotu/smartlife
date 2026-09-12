package com.smartlife.controller;

import com.smartlife.common.LoginUser;
import com.smartlife.common.Result;
import com.smartlife.common.RoleConstants;
import com.smartlife.entity.Voucher;
import com.smartlife.security.RoleGuard;
import com.smartlife.service.MerchantService;
import com.smartlife.service.VoucherService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理端：全平台优惠券 / 秒杀券治理
 *
 * 商家负责自己店铺券的创建与上下架（/merchant/voucher/**）；
 * 平台可巡检、强制下架违规券、删除脏数据。
 */
@RestController
@RequestMapping("/admin/voucher")
public class AdminVoucherGovernController {

    private final VoucherService voucherService;
    private final MerchantService merchantService;

    public AdminVoucherGovernController(VoucherService voucherService, MerchantService merchantService) {
        this.voucherService = voucherService;
        this.merchantService = merchantService;
    }

    /** 全平台券分页（shopId 为空即全平台） */
    @GetMapping("/page")
    public Result<Map<String, Object>> page(@RequestParam(required = false) Long shopId,
                                            @RequestParam(defaultValue = "1") Integer page,
                                            @RequestParam(defaultValue = "10") Integer size) {
        RoleGuard.requireAdmin();
        Map<String, Object> map = new HashMap<>();
        map.put("records", voucherService.adminPage(shopId, page, size));
        map.put("total", voucherService.countVouchers(shopId));
        return Result.ok(map);
    }

    /** 平台强制下架/恢复优惠券（audit_status：0 下架 1 正常） */
    @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @RequestParam Integer auditStatus) {
        LoginUser admin = RoleGuard.requireAdmin();
        Voucher voucher = voucherService.getById(id);
        voucherService.changeAuditStatus(id, auditStatus);
        merchantService.writeAuditLog(admin.getId(), RoleConstants.ROLE_ADMIN,
                auditStatus != null && auditStatus == 1 ? "VOUCHER_RESTORE" : "VOUCHER_BAN",
                "VOUCHER", id, voucher.getTitle());
        return Result.ok();
    }

    /** 删除违规券 */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        LoginUser admin = RoleGuard.requireAdmin();
        Voucher voucher = voucherService.getById(id);
        voucherService.adminRemove(id);
        merchantService.writeAuditLog(admin.getId(), RoleConstants.ROLE_ADMIN,
                "VOUCHER_DELETE", "VOUCHER", id, voucher.getTitle());
        return Result.ok();
    }
}
