package com.smartlife.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 三端统一账号
 * role：1用户 2商家 3平台管理员
 * status：1正常 0封禁（仅平台管理员可操作）
 */
@Data
@TableName("tb_user")
public class User {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String phone;

    /** salt:hash 存储 */
    private String password;

    private String nickname;

    private String avatar;

    private Integer gender;

    /** JSON 数组字符串，如 ["川菜","夜跑"]（用户端饭搭子匹配） */
    private String tags;

    private String address;

    /** 微信号/联系方式（饭搭子展示用） */
    private String wechat;

    /** 1用户 2商家 3平台管理员 */
    private Integer role;

    /** 1正常 0封禁 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
