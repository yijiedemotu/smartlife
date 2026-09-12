package com.smartlife;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智联生活 —— 高并发本地生活与社交聚合平台
 */
@SpringBootApplication
@MapperScan("com.smartlife.mapper")
@EnableScheduling
public class SmartLifeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartLifeApplication.class, args);
    }
}
