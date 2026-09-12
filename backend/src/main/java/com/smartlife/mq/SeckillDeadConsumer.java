package com.smartlife.mq;

import com.rabbitmq.client.Channel;
import com.smartlife.common.JsonUtils;
import com.smartlife.common.MQConstants;
import com.smartlife.dto.SeckillMessage;
import com.smartlife.service.VoucherService;
import com.smartlife.ws.MessagePusher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 死信消费者：秒杀下单连续失败兜底 —— 回补库存 + 通知用户
 */
@Slf4j
@Component
public class SeckillDeadConsumer {

    private final VoucherService voucherService;

    public SeckillDeadConsumer(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @RabbitListener(queues = MQConstants.SECKILL_DEAD_QUEUE)
    public void onDeadMessage(String message, Channel channel,
                              @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        try {
            SeckillMessage msg = JsonUtils.fromJson(message, SeckillMessage.class);
            voucherService.restoreStock(msg.getVoucherId(), msg.getUserId());
            MessagePusher.pusher().seckillResult(msg.getUserId(), false, "下单失败，库存已回补，请重试");
            log.warn("死信补偿完成 userId={} voucherId={}", msg.getUserId(), msg.getVoucherId());
        } catch (Exception e) {
            log.error("死信补偿异常: {}", message, e);
        }
        channel.basicAck(tag, false);
    }
}
