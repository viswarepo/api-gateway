package com.sms.apigateway.cache;

import com.sms.apigateway.config.GatewayProperties;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Redis-backed cache for read-heavy catalog responses, scoped per tenant.
 * Cached entries are tracked in a per-organization Redis Set so a write can
 * invalidate exactly that tenant's cached reads (delete by known key)
 * rather than scanning the keyspace with KEYS/SCAN, which is best avoided
 * against a production Redis instance.
 */
@Component
public class RedisCatalogCacheService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final Duration ttl;

    public RedisCatalogCacheService(ReactiveStringRedisTemplate redisTemplate, GatewayProperties properties) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofSeconds(properties.cache().ttlSeconds());
    }

    public Mono<String> get(String cacheKey) {
        return redisTemplate.opsForValue().get(cacheKey);
    }

    public Mono<Void> put(String organizationId, String cacheKey, String value) {
        String registryKey = registryKey(organizationId);
        return redisTemplate.opsForValue().set(cacheKey, value, ttl)
                .then(redisTemplate.opsForSet().add(registryKey, cacheKey))
                // Safety net in case invalidate() is never triggered for this org (e.g. no writes ever
                // happen): the registry itself expires too, just on a longer horizon than individual entries.
                .then(redisTemplate.expire(registryKey, ttl.multipliedBy(2)))
                .then();
    }

    /** Deletes every cached response for this tenant. Called after a successful write to a cacheable resource type. */
    public Mono<Void> invalidate(String organizationId) {
        String registryKey = registryKey(organizationId);
        return redisTemplate.opsForSet().members(registryKey)
                .collectList()
                .flatMap(keys -> keys.isEmpty()
                        ? Mono.just(0L)
                        : redisTemplate.delete(keys.toArray(new String[0])))
                .then(redisTemplate.delete(registryKey))
                .then();
    }

    private String registryKey(String organizationId) {
        return "cache:keys:" + organizationId;
    }
}
