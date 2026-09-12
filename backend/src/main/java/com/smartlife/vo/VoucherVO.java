package com.smartlife.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 优惠券展示视图（含店铺信息与实时剩余库存）
 */
@Data
public class VoucherVO {

    private Long id;
    private Long shopId;
    private String title;
    private String subTitle;
    private String rules;
    /** 购买价(分) */
    private Integer payValue;
    /** 券面价值(分) */
    private Integer actualValue;
    /** 1 普通代金券 2 秒杀券 */
    private Integer type;
    private Integer stockLeft;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;

    private String shopName;
    private String shopImage;
    private String shopArea;
}
