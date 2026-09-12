package com.smartlife.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Redisson 客户端：提供分布式锁（lock() 默认看门狗自动续期 30s）
 * 单机模式；生产可通过环境变量 REDIS_PASSWORD 注入密码（与 lettuce 共用同一份配置）
 */
@Configuration
public class RedissonConfig {

    @Value("${smartlife.redisson.address:redis://localhost:6379}")
    private String address;

    @Value("${smartlife.redisson.password:}")
    private String password;

    @Value("${smartlife.redisson.database:0}")
    private int database;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        SingleServerConfig server = config.useSingleServer()
                .setAddress(address)
                .setDatabase(database);
        if (StringUtils.hasText(password)) {
            server.setPassword(password);
        }
        return Redisson.create(config);
    }
}
