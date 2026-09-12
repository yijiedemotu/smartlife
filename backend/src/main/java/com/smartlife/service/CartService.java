package com.smartlife.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartlife.common.BusinessException;
import com.smartlife.common.RedisConstants;
import com.smartlife.entity.Product;
import com.smartlife.entity.Shop;
import com.smartlife.mapper.ProductMapper;
import com.smartlife.mapper.ShopMapper;
import com.smartlife.vo.CartItemVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 购物车服务：Redis Hash 存储 cart:{userId} field=productId value=数量
 * 商品信息在读取时回表，保证价格/名称实时
 */
@Service
public class CartService {

    private final ProductMapper productMapper;
    private final ShopMapper shopMapper;
    private final StringRedisTemplate redis;

    public CartService(ProductMapper productMapper, ShopMapper shopMapper, StringRedisTemplate redis) {
        this.productMapper = productMapper;
        this.shopMapper = shopMapper;
        this.redis = redis;
    }

    public void add(Long userId, Long productId, int count) {
        Product product = productMapper.selectById(productId);
        if (product == null || product.getStatus() != 1) {
            throw new BusinessException("商品不存在或已下架");
        }
        redis.opsForHash().increment(RedisConstants.cartKey(userId),
                String.valueOf(productId), count);
    }

    public void update(Long userId, Long productId, int count) {
        if (count <= 0) {
            remove(userId, productId);
            return;
        }
        redis.opsForHash().put(RedisConstants.cartKey(userId), String.valueOf(productId), String.valueOf(count));
    }

    public void remove(Long userId, Long productId) {
        redis.opsForHash().delete(RedisConstants.cartKey(userId), String.valueOf(productId));
    }

    public void clear(Long userId) {
        redis.delete(RedisConstants.cartKey(userId));
    }

    public List<CartItemVO> list(Long userId) {
        Map<Object, Object> entries = redis.opsForHash().entries(RedisConstants.cartKey(userId));
        List<CartItemVO> result = new ArrayList<>();
        if (entries.isEmpty()) {
            return result;
        }
        List<Long> productIds = entries.keySet().stream()
                .map(k -> Long.valueOf(k.toString())).collect(Collectors.toList());
        Map<Long, Product> products = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        List<Long> shopIds = products.values().stream().map(Product::getShopId).distinct().collect(Collectors.toList());
        Map<Long, Shop> shops = shopIds.isEmpty() ? Map.of()
                : shopMapper.selectBatchIds(shopIds).stream().collect(Collectors.toMap(Shop::getId, s -> s));
        String cartKey = RedisConstants.cartKey(userId);
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            Long productId = Long.valueOf(entry.getKey().toString());
            Product product = products.get(productId);
            if (product == null) {
                redis.opsForHash().delete(cartKey, entry.getKey());
                continue;
            }
            int count = Integer.parseInt(entry.getValue().toString());
            CartItemVO vo = new CartItemVO();
            vo.setProductId(productId);
            vo.setProductName(product.getName());
            vo.setProductImage(product.getImages());
            vo.setPrice(product.getPrice());
            vo.setCount(count);
            vo.setSubtotal(product.getPrice() * count);
            vo.setShopId(product.getShopId());
            Shop shop = shops.get(product.getShopId());
            if (shop != null) {
                vo.setShopName(shop.getName());
                vo.setShopArea(shop.getArea());
            }
            result.add(vo);
        }
        result.sort(Comparator.comparing(CartItemVO::getShopId).thenComparing(CartItemVO::getProductId));
        return result;
    }

    /** 购物车角标：商品件数合计 */
    public int totalCount(Long userId) {
        Map<Object, Object> entries = redis.opsForHash().entries(RedisConstants.cartKey(userId));
        int total = 0;
        for (Object v : entries.values()) {
            total += Integer.parseInt(v.toString());
        }
        return total;
    }
}
