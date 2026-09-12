package com.smartlife.init;

import com.smartlife.entity.Shop;
import com.smartlife.service.ShopService;
import com.smartlife.service.VoucherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动初始化（幂等，异常不影响服务启动）：
 * 1) GEO 索引全量重建 + 布隆过滤器装载店铺 id
 * 2) 秒杀券库存缓存回填
 * 3) 店铺类型缓存预热
 */
@Slf4j
@Component
public class DataInitRunner implements ApplicationRunner {

    private final ShopService shopService;
    private final VoucherService voucherService;

    public DataInitRunner(ShopService shopService, VoucherService voucherService) {
        this.shopService = shopService;
        this.voucherService = voucherService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<Shop> shops = shopService.listVisible();
            shopService.rebuildIndexes(shops);
            log.info("索引初始化完成：布隆过滤器 + GEO 装载 {} 家可展示店铺", shops.size());
        } catch (Exception e) {
            log.warn("GEO/布隆初始化跳过（Redis 可能未就绪）: {}", e.getMessage());
        }
        try {
            voucherService.syncSeckillStocks();
        } catch (Exception e) {
            log.warn("秒杀库存缓存回填跳过: {}", e.getMessage());
        }
    }
}
