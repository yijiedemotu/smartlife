package com.smartlife.mq;

import com.rabbitmq.client.Channel;
import com.smartlife.common.BusinessException;
import com.smartlife.common.JsonUtils;
import com.smartlife.common.MQConstants;
import com.smartlife.dto.SeckillMessage;
import com.smartlife.service.VoucherOrderService;
import com.smartlife.service.VoucherService;
import com.smartlife.ws.MessagePusher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 秒杀下单消费者：异步落库（削峰填谷）
 * - 成功：ACK + WS 推送用户
 * - 重复（幂等，唯一索引）：直接 ACK
 * - 业务失败（售罄/时间窗）：回补 Redis 库存 + ACK + WS 推送失败
 * - 未知异常：NACK 进死信队列兜底，由 DeadConsumer 统一补偿
 */
@Slf4j
@Component
public class SeckillOrderConsumer {

    private final VoucherOrderService voucherOrderService;
    private final VoucherService voucherService;

    public SeckillOrderConsumer(VoucherOrderService voucherOrderService, VoucherService voucherService) {
        this.voucherOrderService = voucherOrderService;
        this.voucherService = voucherService;
    }

    @RabbitListener(queues = MQConstants.SECKILL_ORDER_QUEUE)
    public void onMessage(String message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws Exception {
        SeckillMessage msg;
        try {
            msg = JsonUtils.fromJson(message, SeckillMessage.class);
        } catch (Exception e) {
            log.warn("秒杀消息解析失败: {}", message);
            channel.basicAck(tag, false);
            return;
        }
        try {
            voucherOrderService.createVoucherOrderTx(msg.getUserId(), msg.getVoucherId());
            MessagePusher.pusher().seckillResult(msg.getUserId(), true, "抢购成功，券已放入卡包");
            channel.basicAck(tag, false);
        } catch (DuplicateKeyException dup) {
            // 幂等：订单已存在（可能是重复投递/已抢过），直接确认
            log.info("秒杀重复订单 userId={} voucherId={}", msg.getUserId(), msg.getVoucherId());
            channel.basicAck(tag, false);
        } catch (BusinessException b) {
            // 失败补偿：回补库存 + 解除一人一单标记
            voucherService.restoreStock(msg.getVoucherId(), msg.getUserId());
            MessagePusher.pusher().seckillResult(msg.getUserId(), false, b.getMessage());
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("秒杀下单未知异常，转入死信队列 userId={} voucherId={}", msg.getUserId(), msg.getVoucherId(), e);
            channel.basicNack(tag, false, false);
        }
    }
}
