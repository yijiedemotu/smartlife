package com.smartlife.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class ChatSendDTO {

    @NotNull(message = "接收人不能为空")
    private Long toId;

    @NotBlank(message = "消息内容不能为空")
    private String content;
}
