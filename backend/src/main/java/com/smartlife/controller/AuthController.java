package com.smartlife.controller;

import com.smartlife.common.Result;
import com.smartlife.dto.LoginDTO;
import com.smartlife.dto.MerchantRegisterDTO;
import com.smartlife.dto.RegisterDTO;
import com.smartlife.service.UserService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Map;

/**
 * 三端统一认证入口（免拦截，见 WebMvcConfig.EXCLUDE）
 *
 *   POST /auth/register           用户端注册（role=1）
 *   POST /auth/register/merchant  商家入驻注册（role=2 + 自动提交入驻申请单）
 *   POST /auth/login              三端统一登录，返回角色与端侧首页路径
 *
 * 管理员账号不开放注册：由已有管理员在数据库/管理端开通，避免越权提权。
 */
@Validated
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /** 用户端注册 */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        userService.register(dto);
        return Result.ok();
    }

    /** 商家入驻注册：注册即提交入驻申请，等待平台审核 */
    @PostMapping("/register/merchant")
    public Result<Void> registerMerchant(@Valid @RequestBody MerchantRegisterDTO dto) {
        userService.registerMerchant(dto);
        return Result.ok();
    }

    /** 三端统一登录 */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(userService.login(dto));
    }
}
