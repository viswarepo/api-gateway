package com.sms.apigateway.service;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;

/**
 * Server-side revocation for otherwise-stateless JWTs, backed by Redis
 * (already used elsewhere in this gateway for rate limiting). Revoked
 * tokens are keyed by a SHA-256 hash of the raw token - not a jti claim -
 * so this works immediately for every token already in flight, including
 * ones issued before this feature existed, with no JwtUtil change needed.
 * Denylist entries expire from Redis at the same moment the token itself
 * would have expired anyway, so the denylist never grows unbounded.
 */
@Component
public class TokenDenylistService {

    private static final String KEY_PREFIX = "token-denylist:";

    private final ReactiveStringRedisTemplate redisTemplate;

    public TokenDenylistService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** Revokes rawToken until it would have naturally expired anyway. */
    public Mono<Boolean> revoke(String rawToken, Date expiration) {
        Duration ttl = Duration.between(Instant.now(), expiration.toInstant());
        if (ttl.isNegative() || ttl.isZero()) {
            return Mono.just(true); // already expired - nothing left to enforce
        }
        return redisTemplate.opsForValue().set(KEY_PREFIX + hash(rawToken), "revoked", ttl);
    }

    public Mono<Boolean> isRevoked(String rawToken) {
        return redisTemplate.hasKey(KEY_PREFIX + hash(rawToken));
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
