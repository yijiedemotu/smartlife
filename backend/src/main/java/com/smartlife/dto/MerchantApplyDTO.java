package com.smartlife.dto;

import lombok.Data;

/**
 * 商家提交/更新入驻资料（已在商家端登录的账号也走同一张申请单）
 */
@Data
public class MerchantApplyDTO {

    private String shopName;

    private Long typeId;

    private String contactName;

    private String contactPhone;

    private String area;

    private String address;

    private Double lon;

    private Double lat;

    private String licenseNo;

    private String licenseImg;

    private String idCardImg;

    private String description;
}
