package com.smartlife.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_orders")
public class Orders {

    public static final int STATUS_PENDING_PAY = 1;
    public static final int STATUS_PAID = 2;
    public static final int STATUS_ACCEPTED = 3;
    public static final int STATUS_DELIVERING = 4;
    public static final int STATUS_FINISHED = 5;
    public static final int STATUS_CANCELED = 6;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String number;

    private Long userId;

    private Long shopId;

    private String shopName;

    private String address;

    /** 金额（分） */
    private Integer amount;

    private Integer status;

    private String remark;

    private LocalDateTime payTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
