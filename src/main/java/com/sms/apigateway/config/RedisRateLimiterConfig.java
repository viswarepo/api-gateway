package com.sms.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisRateLimiterConfig {

    @Bean
    public RedisRateLimiter redisRateLimiter(
            @Value("${gateway.rate-limit.capacity}") int burstCapacity,
            @Value("${gateway.rate-limit.replenish-rate}") int replenishRate) {
        return new RedisRateLimiter(replenishRate, burstCapacity);
    }
}
