package com.smartlife.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 秒杀异步下单消息体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillMessage {

    private Long userId;
    private Long voucherId;
}
