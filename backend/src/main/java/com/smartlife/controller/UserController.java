package com.smartlife.controller;

import com.smartlife.common.Result;
import com.smartlife.common.RoleConstants;
import com.smartlife.common.UserContext;
import com.smartlife.service.UserService;
import com.smartlife.vo.UserVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 三端共用：我的资料 / 更新资料 / 修改密码 / 我的角色信息
 */
@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(userService.myProfile(UserContext.getUserId()));
    }

    @GetMapping("/{id}")
    public Result<UserVO> profile(@PathVariable Long id) {
        return Result.ok(userService.publicProfile(id));
    }

    /**
     * 当前登录者身份信息：角色 + 端侧首页路径
     * 前端刷新后可用它校正本地缓存的路由归属，避免手改 localStorage 越权跳转
     */
    @GetMapping("/me/role")
    public Result<Map<String, Object>> myRole() {
        var user = UserContext.get();
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("role", user.getRole());
        map.put("nickname", user.getNickname());
        map.put("homePath", UserService.homePath(user.getRole()));
        map.put("roleName", roleName(user.getRole()));
        return Result.ok(map);
    }

    @PutMapping("/me")
    public Result<Void> update(@RequestParam(required = false) String nickname,
                               @RequestParam(required = false) String avatar,
                               @RequestParam(required = false) Integer gender,
                               @RequestParam(required = false) String address,
                               @RequestParam(required = false) String wechat,
                               @RequestParam(required = false) String tags) {
        userService.updateProfile(UserContext.getUserId(), nickname, avatar, gender, address, wechat, tags);
        return Result.ok();
    }

    /** 修改密码（用户/商家自助；管理员同样适用） */
    @PostMapping("/me/password")
    public Result<Void> changePassword(@RequestParam String oldPassword,
                                       @RequestParam String newPassword) {
        userService.changePassword(UserContext.getUserId(), oldPassword, newPassword);
        return Result.ok();
    }

    private String roleName(Integer role) {
        if (role == null) {
            return "未知";
        }
        switch (role) {
            case RoleConstants.ROLE_MERCHANT:
                return "商家";
            case RoleConstants.ROLE_ADMIN:
                return "平台管理员";
            default:
                return "用户";
        }
    }
}
