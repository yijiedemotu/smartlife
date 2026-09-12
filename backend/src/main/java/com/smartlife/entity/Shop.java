package com.smartlife.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 店铺（商家经营主体）
 *
 * merchant_id  ：归属商家 tb_user.id（role=2），一个商家一个店铺，库中为唯一索引
 * audit_status ：平台治理状态 0待审核 1已通过 2已驳回 3已停业
 * open_status  ：商家自主营业开关 1营业 0休息
 */
@Data
@TableName("tb_shop")
public class Shop {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    /** 归属商家（tb_user.id, role=2） */
    private Long merchantId;

    private Long typeId;

    private String area;

    private String address;

    /** 店铺联系电话 */
    private String phone;

    private String images;

    /** 店铺简介 */
    private String description;

    private Double lon;

    private Double lat;

    private Double score;

    private Integer popularity;

    /** 0待审核 1已通过 2已驳回 3已停业 */
    private Integer auditStatus;

    /** 审核意见 / 驳回原因 */
    private String auditRemark;

    private LocalDateTime auditTime;

    /** 1营业 0休息 */
    private Integer openStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
