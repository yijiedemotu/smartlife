package com.smartlife.config;

import com.smartlife.common.MQConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 声明：秒杀异步下单主队列 + 死信队列兜底
 */
@Configuration
public class RabbitConfig {

    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange(MQConstants.SECKILL_EXCHANGE, true, false);
    }

    /**
     * 秒杀下单队列：消费失败(nack, requeue=false)的消息进入死信队列兜底回补库存
     */
    @Bean
    public Queue seckillOrderQueue() {
        return QueueBuilder.durable(MQConstants.SECKILL_ORDER_QUEUE)
                .withArgument("x-dead-letter-exchange", MQConstants.SECKILL_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", MQConstants.SECKILL_DEAD_ROUTING)
                .build();
    }

    @Bean
    public Queue seckillDeadQueue() {
        return QueueBuilder.durable(MQConstants.SECKILL_DEAD_QUEUE).build();
    }

    @Bean
    public Binding seckillBinding() {
        return BindingBuilder.bind(seckillOrderQueue())
                .to(seckillExchange()).with(MQConstants.SECKILL_ORDER_ROUTING);
    }

    @Bean
    public Binding seckillDeadBinding() {
        return BindingBuilder.bind(seckillDeadQueue())
                .to(seckillExchange()).with(MQConstants.SECKILL_DEAD_ROUTING);
    }
}
