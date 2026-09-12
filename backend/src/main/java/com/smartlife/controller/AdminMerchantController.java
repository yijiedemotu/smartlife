package com.smartlife.controller;

import com.smartlife.common.LoginUser;
import com.smartlife.common.Result;
import com.smartlife.common.RoleConstants;
import com.smartlife.dto.AuditDTO;
import com.smartlife.entity.MerchantApply;
import com.smartlife.entity.Shop;
import com.smartlife.entity.ShopType;
import com.smartlife.security.RoleGuard;
import com.smartlife.service.MerchantService;
import com.smartlife.service.ShopService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ============================ 管理端：商家入驻与店铺治理 ============================
 *
 * 访问控制：/admin/** 仅 role=3（平台管理员）（见 AuthInterceptor + RoleGuard 双保险）
 *
 * 治理闭环：
 *   商家提交入驻 -> 管理员审核(tb_merchant_apply) -> 通过自动开店 -> 违规可停业/恢复
 */
@RestController
@RequestMapping("/admin")
public class AdminMerchantController {

    private final MerchantService merchantService;
    private final ShopService shopService;

    public AdminMerchantController(MerchantService merchantService, ShopService shopService) {
        this.merchantService = merchantService;
        this.shopService = shopService;
    }

    // ==================== 入驻申请审核 ====================

    /** 入驻/变更申请分页（status：0待审核 1已通过 2已驳回，为空查全部） */
    @GetMapping("/apply/page")
    public Result<Map<String, Object>> applyPage(@RequestParam(required = false) Integer status,
                                                 @RequestParam(defaultValue = "1") Integer page,
                                                 @RequestParam(defaultValue = "10") Integer size) {
        RoleGuard.requireAdmin();
        return Result.ok(merchantService.adminApplyPage(status, page, size));
    }

    /** 审核入驻申请：通过则创建并开通店铺，驳回则记录原因 */
    @PostMapping("/apply/{applyId}/audit")
    public Result<Void> auditApply(@PathVariable Long applyId, @RequestBody AuditDTO dto) {
        LoginUser admin = RoleGuard.requireAdmin();
        merchantService.auditApply(admin.getId(), applyId, dto);
        return Result.ok();
    }

    /** 申请单详情（审核时展开查看资质） */
    @GetMapping("/apply/{applyId}")
    public Result<MerchantApply> applyDetail(@PathVariable Long applyId) {
        RoleGuard.requireAdmin();
        return Result.ok(merchantService.getApply(applyId));
    }

    // ==================== 店铺治理 ====================

    /** 店铺分页：可按审核状态/类目/商家/关键字过滤（平台视角，不隐藏任何店铺） */
    @GetMapping("/shop/page")
    public Result<Map<String, Object>> shopPage(@RequestParam(required = false) Integer auditStatus,
                                                @RequestParam(required = false) Long typeId,
                                                @RequestParam(required = false) Long merchantId,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(defaultValue = "1") Integer page,
                                                @RequestParam(defaultValue = "10") Integer size) {
        RoleGuard.requireAdmin();
        Map<String, Object> map = new HashMap<>();
        map.put("records", shopService.pageShopsForAdmin(page, size, typeId, merchantId, auditStatus, keyword));
        map.put("total", shopService.countShopsForAdmin(typeId, merchantId, auditStatus, keyword));
        return Result.ok(map);
    }

    /** 店铺全量列表（下拉选择用） */
    @GetMapping("/shop/list")
    public Result<List<Shop>> shopList() {
        RoleGuard.requireAdmin();
        return Result.ok(shopService.listAll());
    }

    /** 平台停业 / 恢复店铺（违规治理） */
    @PostMapping("/shop/{shopId}/audit")
    public Result<Void> changeShopStatus(@PathVariable Long shopId, @RequestBody AuditDTO dto) {
        LoginUser admin = RoleGuard.requireAdmin();
        boolean restore = dto != null && Boolean.TRUE.equals(dto.getApproved());
        merchantService.changeShopAuditStatus(admin.getId(), shopId,
                restore ? RoleConstants.SHOP_AUDIT_APPROVED : RoleConstants.SHOP_AUDIT_CLOSED,
                (dto == null || dto.getRemark() == null)
                        ? (restore ? "平台恢复营业" : "平台停业整顿") : dto.getRemark());
        return Result.ok();
    }

    /** 强制删除店铺（历史脏数据清理，同时清理缓存与 GEO 索引） */
    @DeleteMapping("/shop/{shopId}")
    public Result<Void> removeShop(@PathVariable Long shopId) {
        LoginUser admin = RoleGuard.requireAdmin();
        Shop shop = shopService.getById(shopId);
        merchantService.evictCache(shop.getMerchantId());
        shopService.removeShop(shopId);
        merchantService.writeAuditLog(admin.getId(), RoleConstants.ROLE_ADMIN,
                "SHOP_DELETE", "SHOP", shopId, shop.getName());
        return Result.ok();
    }

    // ==================== 店铺类型字典 ====================

    @GetMapping("/type/list")
    public Result<List<ShopType>> typeList() {
        RoleGuard.requireAdmin();
        return Result.ok(shopService.listTypes());
    }

    @PostMapping("/type/save")
    public Result<Void> saveType(@RequestBody ShopType type) {
        LoginUser admin = RoleGuard.requireAdmin();
        shopService.saveType(type);
        merchantService.writeAuditLog(admin.getId(), RoleConstants.ROLE_ADMIN,
                "TYPE_SAVE", "SHOP_TYPE", type.getId(), type.getName());
        return Result.ok();
    }

    @DeleteMapping("/type/{id}")
    public Result<Void> removeType(@PathVariable Long id) {
        LoginUser admin = RoleGuard.requireAdmin();
        shopService.removeType(id);
        merchantService.writeAuditLog(admin.getId(), RoleConstants.ROLE_ADMIN,
                "TYPE_DELETE", "SHOP_TYPE", id, null);
        return Result.ok();
    }

    // ==================== 审计日志 ====================

    /** 平台操作审计日志（审核、停业、封禁等留痕） */
    @GetMapping("/audit/page")
    public Result<Map<String, Object>> auditPage(@RequestParam(defaultValue = "1") Integer page,
                                                 @RequestParam(defaultValue = "20") Integer size) {
        RoleGuard.requireAdmin();
        return Result.ok(merchantService.auditLogPage(page, size));
    }
}
