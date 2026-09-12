package com.smartlife.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 用户端注册入参：注册出来的账号 role 固定为 1（用户），不允许前端指定角色
 * 商家注册请使用 /auth/register/merchant（MerchantRegisterDTO）
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度需在 6~20 位")
    private String password;

    @Size(max = 16, message = "昵称最长 16 字")
    private String nickname;
}
