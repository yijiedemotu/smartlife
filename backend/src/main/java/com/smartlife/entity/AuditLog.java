package com.smartlife.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台操作审计日志：审核入驻、店铺治理、封禁用户等关键动作留痕
 */
@Data
@TableName("tb_audit_log")
public class AuditLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 操作人 tb_user.id */
    private Long actorId;

    /** 操作人角色 2商家 3管理员 */
    private Integer actorRole;

    /** 动作标识，如 SHOP_APPROVE / USER_BAN */
    private String action;

    /** 目标类型 SHOP/USER/ORDER/PRODUCT/VOUCHER/APPLY */
    private String targetType;

    private Long targetId;

    private String detail;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
