package com.sms.apigateway.filter;

import com.sms.apigateway.security.GatewayHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Runs after authentication/authorization, so it can rate-limit per resolved
 * organization rather than per raw IP. Backed by Redis (see RedisRateLimiter)
 * so the limit is enforced consistently even with multiple gateway instances.
 */
@Component
public class RateLimitingGlobalFilter implements GlobalFilter, Ordered {

    // Stable identifier for the "route" bucket used by Spring Cloud Gateway's
    // RedisRateLimiter. All requests share this route id; the per-caller
    // limiting happens via the "id" argument (the resolved organization key).
    private static final String ROUTE_ID = "global";

    private final RedisRateLimiter redisRateLimiter;
    private final GatewayResponseWriter responseWriter;

    public RateLimitingGlobalFilter(RedisRateLimiter redisRateLimiter, GatewayResponseWriter responseWriter) {
        this.redisRateLimiter = redisRateLimiter;
        this.responseWriter = responseWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/actuator") || path.startsWith("/fallback")) {
            return chain.filter(exchange);
        }
        //exchange.getRequest().getHeaders().add("X-Organization-Id","f1b419af-ea20-4f38-b3fe-5d38bfacdfde");
        String organizationId = exchange.getRequest().getHeaders().getFirst(GatewayHeaders.ORGANIZATION_ID_HEADER);
        //String organizationId = "f1b419af-ea20-4f38-b3fe-5d38bfacdfde";
        String key = organizationId != null ? organizationId : "anonymous";

        return redisRateLimiter.isAllowed(ROUTE_ID, key)
                .flatMap(response -> {
                    if (response.isAllowed()) {
                        return chain.filter(exchange);
                    }
                    exchange.getResponse().getHeaders().add(HttpHeaders.RETRY_AFTER, "30");
                    return responseWriter.writeError(exchange, HttpStatus.TOO_MANY_REQUESTS,
                            "Rate limit exceeded for organization " + key);
                });
    }

    @Override
    public int getOrder() {
        return -40;
    }
}