package com.smartlife.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_voucher_order")
public class VoucherOrder {

    public static final int STATUS_UNUSED = 1;
    public static final int STATUS_USED = 2;
    public static final int STATUS_EXPIRED = 3;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long voucherId;

    private Long shopId;

    private String voucherTitle;

    private Integer actualValue;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private LocalDateTime useTime;
}
