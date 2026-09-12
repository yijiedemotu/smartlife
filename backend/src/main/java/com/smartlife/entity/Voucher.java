package com.smartlife.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_voucher")
public class Voucher {

    /** 普通代金券 */
    public static final int TYPE_NORMAL = 1;
    /** 秒杀券 */
    public static final int TYPE_SECOND_KILL = 2;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long shopId;

    private String title;

    private String subTitle;

    private String rules;

    /** 购买价格（分） */
    private Integer payValue;

    /** 券面金额（分） */
    private Integer actualValue;

    private Integer type;

    /** 商家自主上下架：1上架 0下架 */
    private Integer status;

    /** 平台审核：1正常 0平台强制下架（违规券治理） */
    private Integer auditStatus;

    private Integer stock;

    private Integer sold;

    private LocalDateTime beginTime;

    private LocalDateTime endTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
