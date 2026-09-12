package com.smartlife.task;

import com.smartlife.entity.Shop;
import com.smartlife.service.ShopService;
import com.smartlife.service.VoucherService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 缓存预热定时任务（Spring Scheduler + Redisson 分布式锁防重复执行）：
 * - 热点店铺列表与详情提前写入本地/Redis 多级缓存，错峰避免冷启动穿透
 * - 秒杀券库存缓存巡检回填
 */
@Slf4j
@Component
public class CachePreheatTask {

    private final ShopService shopService;
    private final VoucherService voucherService;
    private final RedissonClient redisson;

    public CachePreheatTask(ShopService shopService, VoucherService voucherService, RedissonClient redisson) {
        this.shopService = shopService;
        this.voucherService = voucherService;
        this.redisson = redisson;
    }

    @Scheduled(cron = "0 0/30 * * * ?")
    public void preheat() {
        RLock lock = redisson.getLock("smartlife:task:preheat");
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            if (!locked) {
                return;
            }
            // 预热热点店铺（写列表缓存 + 详情缓存）
            List<Shop> hot = shopService.hotShops();
            for (Shop shop : hot) {
                shopService.queryById(shop.getId());
            }
            // 秒杀库存缓存巡检
            voucherService.syncSeckillStocks();
            log.info("缓存预热完成，热点店铺 {} 家", hot.size());
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }
}
