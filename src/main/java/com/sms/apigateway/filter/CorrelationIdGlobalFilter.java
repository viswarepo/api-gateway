package com.sms.apigateway.filter;

import com.sms.apigateway.security.GatewayHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Propagates (or generates) a correlation ID so a single client request can
 * be traced across the gateway and whichever downstream service(s) it hits.
 * Runs first on the way in and is available to every later filter, including
 * the request logger.
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String existing = exchange.getRequest().getHeaders().getFirst(GatewayHeaders.CORRELATION_ID_HEADER);
        String correlationId = (existing != null && !existing.isBlank()) ? existing : UUID.randomUUID().toString();

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(GatewayHeaders.CORRELATION_ID_HEADER, correlationId)
                .build();

        exchange.getResponse().getHeaders().add(GatewayHeaders.CORRELATION_ID_HEADER, correlationId);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
