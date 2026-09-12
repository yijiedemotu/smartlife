package com.smartlife.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 商家入驻注册入参：注册账号(role=2) 并同时提交第一张入驻申请单
 * 管理员账号不开放注册，只能由已有管理员在管理端创建，避免越权提权。
 */
@Data
public class MerchantRegisterDTO {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度需在 6~20 位")
    private String password;

    @Size(max = 16, message = "昵称最长 16 字")
    private String nickname;

    // ---------------- 店铺资料 ----------------
    @NotBlank(message = "店铺名称不能为空")
    @Size(max = 64, message = "店铺名称最长 64 字")
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
