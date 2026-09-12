package com.smartlife.security;

import com.smartlife.common.BusinessException;
import com.smartlife.common.LoginUser;
import com.smartlife.common.UserContext;

/**
 * 端侧访问守卫：在 Service 层做一次"当前登录者是否被允许访问该端"的显式校验。
 *
 * 拦截器已按路径前缀做了粗粒度角色控制，本类用于：
 *   1) 定时任务/内部调用等无 Web 上下文的场景，给出清晰报错
 *   2) 代码可读性：Service 里一眼看出该方法的调用方身份要求
 */
public final class RoleGuard {

    private RoleGuard() {
    }

    /** 必须是商家本人（管理员不通过，用于"仅商家自己"的敏感写操作） */
    public static LoginUser requireMerchant() {
        LoginUser user = UserContext.get();
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (!user.isMerchant()) {
            throw new BusinessException("该操作需要商家账号");
        }
        return user;
    }

    /** 商家或管理员（管理员具备平台级代管能力） */
    public static LoginUser requireShopOperator() {
        LoginUser user = UserContext.get();
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (!user.canOperateShop()) {
            throw new BusinessException("该操作需要商家或管理员账号");
        }
        return user;
    }

    /** 必须是平台管理员 */
    public static LoginUser requireAdmin() {
        LoginUser user = UserContext.get();
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (!user.isAdmin()) {
            throw new BusinessException("该操作需要平台管理员账号");
        }
        return user;
    }
}
