package com.smartlife.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录用户上下文模型：从 JWT 中解析后放入 ThreadLocal
 *
 * 三端角色：1=用户端 2=商家端 3=管理端
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {

    /** 用户端：浏览下单、社交、签到 */
    public static final int ROLE_USER = 1;
    /** 商家端：经营自己的店铺（商品/券/订单/看板） */
    public static final int ROLE_MERCHANT = 2;
    /** 管理端：平台治理（审核入驻、店铺/用户/订单治理、平台看板） */
    public static final int ROLE_ADMIN = 3;

    private Long id;
    private Integer role;
    private String nickname;

    public boolean isMerchant() {
        return role != null && role == ROLE_MERCHANT;
    }

    public boolean isAdmin() {
        return role != null && role == ROLE_ADMIN;
    }

    /** 是否具备经营权限（商家或管理员，管理员可代管） */
    public boolean canOperateShop() {
        return isMerchant() || isAdmin();
    }
}
