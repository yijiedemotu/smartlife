package com.smartlife.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商家入驻 / 资料变更申请单
 *
 * 流程：商家提交（status=0待审核）-> 平台管理员审核（1通过 / 2驳回）
 * 通过后：首次入驻会据此创建/绑定 tb_shop 并置 audit_status=1
 */
@Data
@TableName("tb_merchant_apply")
public class MerchantApply {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 申请商家 tb_user.id */
    private Long merchantId;

    /** 关联店铺（首次入驻通过后回填） */
    private Long shopId;

    /** 1首次入驻 2资料变更 */
    private Integer type;

    private String shopName;

    private Long typeId;

    private String contactName;

    private String contactPhone;

    private String area;

    private String address;

    private Double lon;

    private Double lat;

    /** 营业执照号 */
    private String licenseNo;

    private String licenseImg;

    private String idCardImg;

    private String description;

    /** 0待审核 1已通过 2已驳回 */
    private Integer status;

    private String auditRemark;

    /** 审核人 tb_user.id（管理员） */
    private Long auditorId;

    private LocalDateTime auditTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
