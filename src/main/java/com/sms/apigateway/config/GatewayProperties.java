package com.sms.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding of the "gateway.*" configuration namespace. Spring Boot 3
 * supports constructor binding of records directly, so this doubles as a
 * demonstration of Java 21 records for configuration rather than a
 * traditional getter/setter @ConfigurationProperties class.
 *
 * JWT signing config lives under "jwt.*" (see JwtUtil), not here — this
 * used to also hold a separate "gateway.jwt.*" secret consumed by a
 * different class (JwtService) than the one that mints tokens (JwtUtil),
 * which is what caused login-minted tokens to fail verification. Now
 * there's exactly one JWT class and one secret property.
 */
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(RateLimit rateLimit, Cache cache) {

    /** capacity requests allowed per windowSeconds, per resolved tenant, enforced via Redis. */
    public record RateLimit(int capacity, int windowSeconds) {
    }

    /** ttlSeconds a cached catalog read response may be served before it's considered stale. */
    public record Cache(int ttlSeconds) {
    }
}
