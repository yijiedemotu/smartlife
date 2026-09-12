package com.smartlife.controller;

import com.smartlife.common.LoginUser;
import com.smartlife.common.Result;
import com.smartlife.common.RoleConstants;
import com.smartlife.security.RoleGuard;
import com.smartlife.service.MerchantService;
import com.smartlife.service.UserService;
import com.smartlife.vo.UserVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端：全平台账号治理
 *
 * 平台可检索三端账号、封禁违规用户/商家；管理员账号受保护不可被封禁。
 */
@RestController
@RequestMapping("/admin/user")
public class AdminUserController {

    private final UserService userService;
    private final MerchantService merchantService;

    public AdminUserController(UserService userService, MerchantService merchantService) {
        this.userService = userService;
        this.merchantService = merchantService;
    }

    /** 账号分页（role：1用户 2商家 3管理员；status：1正常 0封禁） */
    @GetMapping("/page")
    public Result<Map<String, Object>> page(@RequestParam(required = false) Integer role,
                                             @RequestParam(required = false) Integer status,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(defaultValue = "1") Integer page,
                                             @RequestParam(defaultValue = "10") Integer size) {
        RoleGuard.requireAdmin();
        return Result.ok(userService.adminPage(page, size, role, status, keyword));
    }

    /** 账号详情 */
    @GetMapping("/{id}")
    public Result<UserVO> detail(@PathVariable Long id) {
        RoleGuard.requireAdmin();
        return Result.ok(userService.publicProfile(id));
    }

    /** 封禁 / 解封账号（status：1正常 0封禁） */
    @PostMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        LoginUser admin = RoleGuard.requireAdmin();
        userService.changeStatus(id, status);
        merchantService.writeAuditLog(admin.getId(), RoleConstants.ROLE_ADMIN,
                status != null && status == RoleConstants.USER_STATUS_BANNED ? "USER_BAN" : "USER_UNBAN",
                "USER", id, null);
        return Result.ok();
    }
}
