package com.smartlife.common;

/**
 * RabbitMQ 交换机/队列/路由键
 */
public class MQConstants {

    public static final String SECKILL_EXCHANGE = "smartlife.seckill.direct";
    public static final String SECKILL_ORDER_QUEUE = "smartlife.seckill.order.queue";
    public static final String SECKILL_ORDER_ROUTING = "seckill.order";

    /** 死信队列（秒杀下单消费失败重试后进入） */
    public static final String SECKILL_DEAD_QUEUE = "smartlife.seckill.order.dead.queue";
    public static final String SECKILL_DEAD_ROUTING = "seckill.order.dead";
}
