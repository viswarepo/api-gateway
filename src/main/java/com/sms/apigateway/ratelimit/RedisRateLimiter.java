package com.sms.apigateway.ratelimit;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

//@Component
public class RedisRateLimiter {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final int replenishRate;
    private final int burstCapacity;
    private final int windowSeconds;

    public RedisRateLimiter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.replenishRate = 1;
        this.burstCapacity = 200;
        this.windowSeconds = 100;
    }

    public Mono<Boolean> tryAcquire(String key) {
        // Example token bucket logic
        return redisTemplate.opsForValue()
                .increment(key)
                .flatMap(count -> {
                    if (count <= burstCapacity) {
                        return Mono.just(true);
                    } else {
                        return Mono.just(false);
                    }
                });
    }

    public int windowSeconds() {
        return windowSeconds;
    }
}
