package com.smartlife.vo;

import lombok.Data;

/**
 * 购物车条目
 */
@Data
public class CartItemVO {

    private Long productId;
    private String productName;
    private String productImage;
    private Integer price;
    private Integer count;
    private Integer subtotal;
    private Long shopId;
    private String shopName;
    private String shopArea;
}
