package com.smartlife.vo;

import com.smartlife.entity.Shop;
import lombok.Data;

/**
 * 附近店铺：店铺 + 距离
 */
@Data
public class ShopNearVO {

    private Shop shop;

    /** 距离，单位米 */
    private Integer distanceM;
}
