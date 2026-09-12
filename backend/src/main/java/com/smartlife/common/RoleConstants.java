package com.smartlife.common;

import com.smartlife.entity.Shop;

/**
 * 三端权限与业务状态常量（集中管理，避免魔法数字散落）
 */
public final class RoleConstants {

    private RoleConstants() {
    }

    // ---------------- 角色：与 tb_user.role 对应 ----------------
    public static final int ROLE_USER = LoginUser.ROLE_USER;
    public static final int ROLE_MERCHANT = LoginUser.ROLE_MERCHANT;
    public static final int ROLE_ADMIN = LoginUser.ROLE_ADMIN;

    // ---------------- 账号状态：tb_user.status ----------------
    public static final int USER_STATUS_NORMAL = 1;
    public static final int USER_STATUS_BANNED = 0;

    // ---------------- 店铺审核状态：tb_shop.audit_status ----------------
    public static final int SHOP_AUDIT_PENDING = 0;
    public static final int SHOP_AUDIT_APPROVED = 1;
    public static final int SHOP_AUDIT_REJECTED = 2;
    public static final int SHOP_AUDIT_CLOSED = 3;

    // ---------------- 入驻申请状态：tb_merchant_apply.status ----------------
    public static final int APPLY_PENDING = 0;
    public static final int APPLY_APPROVED = 1;
    public static final int APPLY_REJECTED = 2;

    /** 入驻申请类型 */
    public static final int APPLY_TYPE_FIRST = 1;
    public static final int APPLY_TYPE_UPDATE = 2;

    /** 店铺营业开关 */
    public static final int SHOP_OPEN = 1;
    public static final int SHOP_REST = 0;

    /** 券的平台审核状态 */
    public static final int VOUCHER_AUDIT_NORMAL = 1;
    public static final int VOUCHER_AUDIT_BANNED = 0;

    public static String shopAuditText(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case SHOP_AUDIT_PENDING:
                return "待审核";
            case SHOP_AUDIT_APPROVED:
                return "营业中";
            case SHOP_AUDIT_REJECTED:
                return "已驳回";
            case SHOP_AUDIT_CLOSED:
                return "已停业";
            default:
                return "未知";
        }
    }

    /** 用户端只展示审核通过且正常营业的店铺 */
    public static boolean visibleToUser(Shop shop) {
        return shop != null
                && shop.getAuditStatus() != null
                && shop.getAuditStatus() == SHOP_AUDIT_APPROVED
                && shop.getOpenStatus() != null
                && shop.getOpenStatus() == SHOP_OPEN;
    }
}
