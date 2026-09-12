package com.smartlife.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartlife.entity.Orders;
import com.smartlife.mapper.OrdersMapper;
import com.smartlife.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 超时未支付订单自动取消（参考苍穹外卖定时任务）
 * 多实例部署下使用 Redisson 分布式锁保证同一时刻仅一台机器执行
 */
@Slf4j
@Component
public class OrderTimeoutTask {

    private static final int TIMEOUT_MINUTES = 15;

    private final OrdersMapper ordersMapper;
    private final OrderService orderService;
    private final RedissonClient redisson;

    public OrderTimeoutTask(OrdersMapper ordersMapper, OrderService orderService, RedissonClient redisson) {
        this.ordersMapper = ordersMapper;
        this.orderService = orderService;
        this.redisson = redisson;
    }

    @Scheduled(cron = "0/30 * * * * ?")
    public void cancelTimeoutOrders() {
        RLock lock = redisson.getLock("smartlife:task:timeout-order");
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            if (!locked) {
                return; // 其它节点正在执行
            }
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);
            List<Orders> expired = ordersMapper.selectList(new LambdaQueryWrapper<Orders>()
                    .eq(Orders::getStatus, Orders.STATUS_PENDING_PAY)
                    .lt(Orders::getCreateTime, cutoff)
                    .last("LIMIT 200"));
            for (Orders order : expired) {
                orderService.autoCancel(order);
                log.info("超时订单自动取消: {}", order.getNumber());
            }
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }
}
