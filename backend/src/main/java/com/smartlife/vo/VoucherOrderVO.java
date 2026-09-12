package com.smartlife.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 券订单视图（用户端"我的券" / 商家端"券核销"共用）
 */
@Data
public class VoucherOrderVO {

    private Long id;
    private Long voucherId;
    private Long shopId;
    private String voucherTitle;
    /** 券面金额(分) */
    private Integer actualValue;
    /** 1 未使用 2 已使用 3 已过期 */
    private Integer status;
    private String shopName;
    private String shopArea;
    /** 领券人昵称（商家端核销对账用） */
    private String userName;
    private LocalDateTime createTime;
    private LocalDateTime useTime;
}
