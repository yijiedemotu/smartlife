package com.smartlife.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartlife.common.BusinessException;
import com.smartlife.entity.Product;
import com.smartlife.mapper.ProductMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品服务
 */
@Service
public class ProductService {

    private final ProductMapper productMapper;

    public ProductService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    /** 某店铺在售商品 */
    public List<Product> listByShop(Long shopId) {
        return productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getShopId, shopId)
                .eq(Product::getStatus, 1)
                .orderByAsc(Product::getId));
    }

    /**
     * 商品分页（商家端传自己的 shopId，平台端传 null 查全平台）
     */
    public List<Product> adminPage(Long shopId, Integer page, Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? 10 : Math.min(size, 100);
        return productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(shopId != null, Product::getShopId, shopId)
                .orderByDesc(Product::getUpdateTime)
                .last("LIMIT " + (p - 1) * s + "," + s));
    }

    public long countByShop(Long shopId) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(shopId != null, Product::getShopId, shopId);
        Long count = productMapper.selectCount(wrapper);
        return count == null ? 0 : count;
    }

    /**
     * 保存商品：商家端调用前必须由 MerchantService.assertShopOwnership 校验归属，
     * 且 shopId 由服务端强制写成自己的店铺（忽略前端传入的其它 shopId，防越权改他人商品）
     */
    public void save(Product product) {
        if (product.getStatus() == null) {
            product.setStatus(1);
        }
        if (product.getSales() == null) {
            product.setSales(0);
        }
        if (product.getStock() == null) {
            product.setStock(0);
        }
        if (product.getId() == null) {
            productMapper.insert(product);
        } else {
            productMapper.updateById(product);
        }
    }

    public void remove(Long id) {
        productMapper.deleteById(id);
    }

    public Product getById(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        return product;
    }
}
