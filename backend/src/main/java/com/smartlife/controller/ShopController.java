package com.smartlife.controller;

import com.smartlife.common.Result;
import com.smartlife.common.RedisConstants;
import com.smartlife.entity.Product;
import com.smartlife.entity.Shop;
import com.smartlife.entity.ShopType;
import com.smartlife.service.ProductService;
import com.smartlife.service.ShopService;
import com.smartlife.vo.ShopNearVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 用户端店铺/商品浏览接口
 */
@RestController
@RequestMapping("/shop")
public class ShopController {

    private final ShopService shopService;
    private final ProductService productService;
    private final StringRedisTemplate redis;

    public ShopController(ShopService shopService, ProductService productService, StringRedisTemplate redis) {
        this.shopService = shopService;
        this.productService = productService;
        this.redis = redis;
    }

    /** 店铺类型列表 */
    @GetMapping("/type/list")
    public Result<List<ShopType>> types() {
        return Result.ok(shopService.listTypes());
    }

    /** 店铺分页浏览 */
    @GetMapping("/list")
    public Result<List<Shop>> list(@RequestParam(required = false) Long typeId,
                                   @RequestParam(defaultValue = "1") Integer page,
                                   @RequestParam(defaultValue = "10") Integer size,
                                   @RequestParam(required = false) String keyword) {
        return Result.ok(shopService.pageShops(page, size, typeId, keyword));
    }

    /** 热点店铺 */
    @GetMapping("/hot")
    public Result<List<Shop>> hot() {
        return Result.ok(shopService.hotShops());
    }

    /** 附近店铺：Redis GEO */
    @GetMapping("/nearby")
    public Result<List<ShopNearVO>> nearby(@RequestParam Double lon,
                                           @RequestParam Double lat,
                                           @RequestParam(required = false) Long typeId,
                                           @RequestParam(required = false) Double radiusKm) {
        return Result.ok(shopService.nearby(lon, lat, typeId, radiusKm));
    }

    /** 店铺详情（命中多级缓存），并计入 HyperLogLog UV */
    @GetMapping("/{id}")
    public Result<Shop> detail(@PathVariable Long id) {
        Shop shop = shopService.queryById(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        // HyperLogLog 页面 UV 统计：uv:{date}:shop:{id}
        String uvKey = RedisConstants.uvKey(LocalDate.now(), "shop:" + id);
        redis.opsForHyperLogLog().add(uvKey, String.valueOf(id));
        return Result.ok(shop);
    }

    /** 店铺商品列表 */
    @GetMapping("/{id}/products")
    public Result<List<Product>> products(@PathVariable Long id) {
        return Result.ok(productService.listByShop(id));
    }
}
