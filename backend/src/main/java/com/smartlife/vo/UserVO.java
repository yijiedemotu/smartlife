package com.smartlife.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户/商家/管理员对外安全视图（隐藏 password），tags 展开为数组便于前端展示
 */
@Data
public class UserVO {

    private Long id;
    private String nickname;
    private String avatar;
    private Integer gender;
    /** 1用户 2商家 3平台管理员 */
    private Integer role;
    /** 1正常 0封禁 */
    private Integer status;
    private String phone;
    private String address;
    /** 微信号（搭子展示/联系） */
    private String wechat;
    private List<String> tags;
    private LocalDateTime createTime;
}
