package com.smartlife.service;

import com.smartlife.common.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * UV 统计：HyperLogLog 以 ~12KB 内存统计百万级去重 UV
 */
@Service
public class UvService {

    private final StringRedisTemplate redis;

    public UvService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 记录一次访问（pageKey 如 home / shop:1 / seckill） */
    public void record(LocalDate date, String pageKey, String visitorId) {
        redis.opsForHyperLogLog().add(RedisConstants.uvKey(date, pageKey), visitorId);
    }

    /** 今日某页面 UV */
    public long countToday(String pageKey) {
        Long size = redis.opsForHyperLogLog().size(RedisConstants.uvKey(LocalDate.now(), pageKey));
        return size == null ? 0 : size;
    }

    /** 今日全站 UV = 各页面 HyperLogLog 之和（近似） */
    public long sumToday() {
        LocalDate today = LocalDate.now();
        Set<String> keys = redis.keys("uv:" + today + ":*");
        long total = 0;
        if (keys != null) {
            for (String key : keys) {
                Long size = redis.opsForHyperLogLog().size(key);
                total += size == null ? 0 : size;
            }
        }
        return total;
    }
}
