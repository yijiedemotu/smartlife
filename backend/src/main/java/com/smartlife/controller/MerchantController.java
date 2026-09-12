package com.smartlife.controller;

import com.smartlife.common.BusinessException;
import com.smartlife.common.LoginUser;
import com.smartlife.common.Result;
import com.smartlife.common.RoleConstants;
import com.smartlife.dto.AuditDTO;
import com.smartlife.dto.MerchantApplyDTO;
import com.smartlife.entity.MerchantApply;
import com.smartlife.entity.Product;
import com.smartlife.entity.Shop;
import com.smartlife.entity.Voucher;
import com.smartlife.security.RoleGuard;
import com.smartlife.service.MerchantService;
import com.smartlife.service.OrderService;
import com.smartlife.service.ProductService;
import com.smartlife.service.ShopService;
import com.smartlife.service.StatsService;
import com.smartlife.service.VoucherOrderService;
import com.smartlife.service.VoucherService;
import com.smartlife.vo.VoucherOrderVO;
import org.springframework.util.StringUtils;
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
 * ============================ 商家端接口 ============================
 *
 * 访问控制：/merchant/** 仅 role=2（商家）与 role=3（管理员）可进入（见 AuthInterceptor）。
 * 数据隔离：所有查询强制带上"当前登录商家名下的店铺 id"，商家无法读写他人店铺数据；
 *          管理员具备平台代管能力，需显式传 shopId 指定要管理的店铺。
 *
 * 接口分区：
 *   /merchant/dashboard/**  经营看板
 *   /merchant/order/**      订单履约
 *   /merchant/product/**    商品管理
 *   /merchant/voucher/**    优惠券管理与核销
 *   /merchant/shop/**       店铺信息与入驻审核状态
 *   /merchant/apply/**      入驻 / 资料变更申请
 */
@RestController
@RequestMapping("/merchant")
public class MerchantController {

    private final MerchantService merchantService;
    private final StatsService statsService;
    private final OrderService orderService;
    private final ProductService productService;
    private final VoucherService voucherService;
    private final VoucherOrderService voucherOrderService;
    private final ShopService shopService;

    public MerchantController(MerchantService merchantService, StatsService statsService,
                              OrderService orderService, ProductService productService,
                              VoucherService voucherService, VoucherOrderService voucherOrderService,
                              ShopService shopService) {
        this.merchantService = merchantService;
        this.statsService = statsService;
        this.orderService = orderService;
        this.productService = productService;
        this.voucherService = voucherService;
        this.voucherOrderService = voucherOrderService;
        this.shopService = shopService;
    }

    // ==================== 经营看板 ====================

    /** 经营总览：今日/累计订单、GMV、待处理单量 + 券核销概览 */
    @GetMapping("/dashboard/overview")
    public Result<Map<String, Object>> overview(@RequestParam(required = false) Long shopId) {
        LoginUser user = RoleGuard.requireShopOperator();
        Shop shop = merchantService.resolveQueryShop(user, shopId);
        Long sid = shop == null ? null : shop.getId();
        Map<String, Object> data = statsService.merchantOverview(sid);
        if (sid != null) {
            data.putAll(voucherOrderService.merchantStats(sid));
            data.put("productCount", productService.countByShop(sid));
            data.put("voucherCount", voucherService.countVouchers(sid));
            data.put("shopName", shop.getName());
            data.put("auditStatus", shop.getAuditStatus());
            data.put("auditText", RoleConstants.shopAuditText(shop.getAuditStatus()));
            data.put("openStatus", shop.getOpenStatus());
        }
        data.put("scope", sid == null ? "PLATFORM" : "SHOP");
        return Result.ok(data);
    }

    /** 近 N 天订单/GMV 趋势 */
    @GetMapping("/dashboard/trend")
    public Result<List<Map<String, Object>>> trend(@RequestParam(required = false) Long shopId,
                                                   @RequestParam(defaultValue = "7") Integer days) {
        LoginUser user = RoleGuard.requireShopOperator();
        Shop shop = merchantService.resolveQueryShop(user, shopId);
        if (shop == null) {
            throw new BusinessException("请先选择要查看的店铺");
        }
        return Result.ok(statsService.merchantTrend(shop.getId(), days == null ? 7 : days));
    }

    /** 订单状态分布 */
    @GetMapping("/dashboard/orderStatus")
    public Result<List<Map<String, Object>>> orderStatus(@RequestParam(required = false) Long shopId) {
        LoginUser user = RoleGuard.requireShopOperator();
        Shop shop = merchantService.resolveQueryShop(user, shopId);
        if (shop == null) {
            throw new BusinessException("请先选择要查看的店铺");
        }
        return Result.ok(statsService.merchantOrderStatus(shop.getId()));
    }

    /** 热销商品 TopN */
    @GetMapping("/dashboard/topProducts")
    public Result<List<Map<String, Object>>> topProducts(@RequestParam(required = false) Long shopId) {
        LoginUser user = RoleGuard.requireShopOperator();
        Shop shop = merchantService.resolveQueryShop(user, shopId);
        if (shop == null) {
            throw new BusinessException("请先选择要查看的店铺");
        }
        return Result.ok(statsService.merchantTopProducts(shop.getId()));
    }

    // ==================== 订单履约 ====================

    @GetMapping("/order/page")
    public Result<Map<String, Object>> orderPage(@RequestParam(required = false) Integer status,
                                                 @RequestParam(defaultValue = "1") Integer page,
                                                 @RequestParam(defaultValue = "10") Integer size,
                                                 @RequestParam(required = false) Long shopId) {
        LoginUser user = RoleGuard.requireShopOperator();
        Shop shop = merchantService.resolveQueryShop(user, shopId);
        return Result.ok(orderService.adminPage(shop == null ? null : shop.getId(), status, page, size));
    }

    @PostMapping("/order/{orderId}/accept")
    public Result<Void> accept(@PathVariable Long orderId) {
        orderService.accept(orderId, currentMerchantScope());
        return Result.ok();
    }

    @PostMapping("/order/{orderId}/deliver")
    public Result<Void> deliver(@PathVariable Long orderId) {
        orderService.deliver(orderId, currentMerchantScope());
        return Result.ok();
    }

    @PostMapping("/order/{orderId}/finish")
    public Result<Void> finish(@PathVariable Long orderId) {
        orderService.finish(orderId, currentMerchantScope());
        return Result.ok();
    }

    @PostMapping("/order/{orderId}/cancel")
    public Result<Void> cancel(@PathVariable Long orderId) {
        orderService.cancelByMerchant(orderId, currentMerchantScope());
        return Result.ok();
    }

    // ==================== 商品管理 ====================

    @GetMapping("/product/list")
    public Result<Map<String, Object>> productList(@RequestParam(defaultValue = "1") Integer page,
                                                   @RequestParam(defaultValue = "10") Integer size,
                                                   @RequestParam(required = false) Long shopId) {
        LoginUser user = RoleGuard.requireShopOperator();
        Shop shop = merchantService.resolveQueryShop(user, shopId);
        Long sid = shop == null ? null : shop.getId();
        Map<String, Object> map = new HashMap<>();
        map.put("records", productService.adminPage(sid, page, size));
        map.put("total", productService.countByShop(sid));
        return Result.ok(map);
    }

    @PostMapping("/product/save")
    public Result<Void> productSave(@RequestBody Product product) {
        LoginUser user = RoleGuard.requireShopOperator();
        // 经营类写操作：必须已有过审店铺
        Shop shop = merchantService.resolveOperateShop(user, product.getShopId(), true);
        // 强制归属到自己的店铺，忽略前端伪造的 shopId
        product.setShopId(shop.getId());
        if (product.getId() != null) {
            merchantService.assertShopOwnership(productService.getById(product.getId()).getShopId(),
                    user.isAdmin() ? null : user.getId());
        }
        productService.save(product);
        return Result.ok();
    }

    @DeleteMapping("/product/{id}")
    public Result<Void> productRemove(@PathVariable Long id) {
        LoginUser user = RoleGuard.requireShopOperator();
        Product exists = productService.getById(id);
        merchantService.assertShopOwnership(exists.getShopId(), user.isAdmin() ? null : user.getId());
        productService.remove(id);
        return Result.ok();
    }

    // ==================== 优惠券管理 ====================

    @GetMapping("/voucher/page")
    public Result<Map<String, Object>> voucherPage(@RequestParam(defaultValue = "1") Integer page,
                                                   @RequestParam(defaultValue = "10") Integer size,
                                                   @RequestParam(required = false) Long shopId) {
        LoginUser user = RoleGuard.requireShopOperator();
        Shop shop = merchantService.resolveQueryShop(user, shopId);
        Long sid = shop == null ? null : shop.getId();
        Map<String, Object> map = new HashMap<>();
        map.put("records", voucherService.adminPage(sid, page, size));
        map.put("total", voucherService.countVouchers(sid));
        return Result.ok(map);
    }

    @PostMapping("/voucher/save")
    public Result<Void> voucherSave(@RequestBody Voucher voucher) {
        LoginUser user = RoleGuard.requireShopOperator();
        Shop shop = merchantService.resolveOperateShop(user, voucher.getShopId(), true);
        voucher.setShopId(shop.getId());
        if (voucher.getId() != null) {
            Voucher exists = voucherService.getById(voucher.getId());
            merchantService.assertShopOwnership(exists.getShopId(), user.isAdmin() ? null : user.getId());
            voucher.setSold(exists.getSold());
        }
        voucherService.adminSave(voucher);
        return Result.ok();
    }

    @DeleteMapping("/voucher/{id}")
    public Result<Void> voucherRemove(@PathVariable Long id) {
        LoginUser user = RoleGuard.requireShopOperator();
        Voucher exists = voucherService.getById(id);
        merchantService.assertShopOwnership(exists.getShopId(), user.isAdmin() ? null : user.getId());
        voucherService.adminRemove(id);
        return Result.ok();
    }

    // ==================== 券核销 ====================

    @GetMapping("/voucher/order/page")
    public Result<Map<String, Object>> voucherOrderPage(@RequestParam(required = false) Integer status,
                                                        @RequestParam(defaultValue = "1") Integer page,
                                                        @RequestParam(defaultValue = "10") Integer size) {
        LoginUser user = RoleGuard.requireShopOperator();
        Shop shop = merchantService.resolveOperateShop(user, null, false);
        return Result.ok(voucherOrderService.merchantPage(shop.getId(), status, page, size));
    }

    @PostMapping("/voucher/order/{orderId}/use")
    public Result<Void> voucherUse(@PathVariable Long orderId) {
        LoginUser user = RoleGuard.requireShopOperator();
        Shop shop = merchantService.resolveOperateShop(user, null, false);
        voucherOrderService.merchantUse(shop.getId(), orderId);
        return Result.ok();
    }

    // ==================== 店铺管理与入驻 ====================

    /** 我的店铺：含审核状态/审核意见，供商家端首屏引导 */
    @GetMapping("/shop/mine")
    public Result<Map<String, Object>> myShop() {
        LoginUser user = RoleGuard.requireShopOperator();
        Map<String, Object> map = new HashMap<>();
        if (user.isAdmin()) {
            // 管理员没有个人店铺，返回全平台店铺列表供选择代管
            map.put("admin", true);
            map.put("shops", shopService.pageShopsForAdmin(1, 100, null, null, null, null));
            return Result.ok(map);
        }
        Shop shop = merchantService.shopOfMerchant(user.getId());
        map.put("admin", false);
        map.put("shop", shop);
        map.put("auditText", shop == null ? "未入驻" : RoleConstants.shopAuditText(shop.getAuditStatus()));
        Long pending = merchantService.countPendingApplies(user.getId());
        map.put("hasPendingApply", pending != null && pending > 0);
        return Result.ok(map);
    }

    /** 商家维护可自助修改的资料（地址/经纬度/图片/电话） */
    @PostMapping("/shop/profile")
    public Result<Void> updateProfile(@RequestBody Shop form) {
        LoginUser user = RoleGuard.requireMerchant();
        merchantService.updateShopProfile(user.getId(), form);
        return Result.ok();
    }

    /** 营业/休息开关 */
    @PostMapping("/shop/open")
    public Result<Void> toggleOpen(@RequestParam Boolean open) {
        LoginUser user = RoleGuard.requireMerchant();
        merchantService.toggleOpen(user.getId(), Boolean.TRUE.equals(open));
        return Result.ok();
    }

    /** 提交入驻 / 资料变更申请 */
    @PostMapping("/apply/submit")
    public Result<MerchantApply> submitApply(@RequestBody MerchantApplyDTO dto) {
        LoginUser user = RoleGuard.requireMerchant();
        MerchantApply apply = merchantService.submitApply(user.getId(), user.getNickname(), null, dto);
        return Result.ok(apply);
    }

    /** 我的申请记录 */
    @GetMapping("/apply/page")
    public Result<Map<String, Object>> applyPage(@RequestParam(defaultValue = "1") Integer page,
                                                 @RequestParam(defaultValue = "10") Integer size) {
        LoginUser user = RoleGuard.requireMerchant();
        Map<String, Object> map = new HashMap<>();
        map.put("records", merchantService.myApplies(user.getId(), page, size));
        map.put("total", merchantService.countMyApplies(user.getId()));
        return Result.ok(map);
    }

    // ==================== 内部工具 ====================

    /**
     * 订单流转的商家作用域：
     *   商家 -> 自己的 userId（OrderService 内比对 shop.merchant_id，天然隔离）
     *   管理员 -> null（平台代管，不限制归属）
     */
    private Long currentMerchantScope() {
        LoginUser user = RoleGuard.requireShopOperator();
        return user.isAdmin() ? null : user.getId();
    }
}
