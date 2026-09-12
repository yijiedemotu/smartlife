package com.smartlife.controller;

import com.smartlife.common.LoginUser;
import com.smartlife.common.Result;
import com.smartlife.common.RoleConstants;
import com.smartlife.entity.Product;
import com.smartlife.security.RoleGuard;
import com.smartlife.service.MerchantService;
import com.smartlife.service.ProductService;
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
 * 管理端：全平台商品巡检
 *
 * 平台的定位是"治理"而非"经营"：可查看全平台商品、下架违规商品、删除脏数据；
 * 商品的新增与日常维护由商家在 /merchant/product/** 完成。
 */
@RestController
@RequestMapping("/admin/product")
public class AdminProductGovernController {

    private final ProductService productService;
    private final MerchantService merchantService;

    public AdminProductGovernController(ProductService productService, MerchantService merchantService) {
        this.productService = productService;
        this.merchantService = merchantService;
    }

    /** 全平台商品分页（可按店铺过滤，shopId 为空即全平台） */
    @GetMapping("/page")
    public Result<Map<String, Object>> page(@RequestParam(required = false) Long shopId,
                                            @RequestParam(defaultValue = "1") Integer page,
                                            @RequestParam(defaultValue = "10") Integer size) {
        RoleGuard.requireAdmin();
        Map<String, Object> map = new HashMap<>();
        map.put("records", productService.adminPage(shopId, page, size));
        map.put("total", productService.countByShop(shopId));
        return Result.ok(map);
    }

    /** 平台强制下架/上架商品（治理，写入审计日志） */
    @PostMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        LoginUser admin = RoleGuard.requireAdmin();
        Product product = productService.getById(id);
        product.setStatus(status);
        productService.save(product);
        merchantService.writeAuditLog(admin.getId(), RoleConstants.ROLE_ADMIN,
                status != null && status == 1 ? "PRODUCT_UP" : "PRODUCT_DOWN",
                "PRODUCT", id, product.getName());
        return Result.ok();
    }

    /** 删除违规商品 */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        LoginUser admin = RoleGuard.requireAdmin();
        Product product = productService.getById(id);
        productService.remove(id);
        merchantService.writeAuditLog(admin.getId(), RoleConstants.ROLE_ADMIN,
                "PRODUCT_DELETE", "PRODUCT", id, product.getName());
        return Result.ok();
    }
}
