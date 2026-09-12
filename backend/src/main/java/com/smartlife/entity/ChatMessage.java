package com.smartlife.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 饭搭子私信
 */
@Data
@TableName("tb_message")
public class ChatMessage {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long fromId;

    private Long toId;

    private String content;

    /** 0 未读 1 已读 */
    private Integer readFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
