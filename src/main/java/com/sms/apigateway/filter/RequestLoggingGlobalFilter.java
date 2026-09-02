package com.sms.apigateway.filter;

import com.sms.apigateway.security.GatewayHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Logs one line per request with enough context to correlate with downstream
 * service logs (via X-Request-Id) and to spot slow or failing routes.
 * Registered with a low order so its timer wraps auth, rate limiting, and
 * the proxied call — the logged duration reflects the whole request.
 */
@Component
public class RequestLoggingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.currentTimeMillis();
        ServerHttpRequest request = exchange.getRequest();

        return chain.filter(exchange).doFinally(signalType -> {
            long durationMs = System.currentTimeMillis() - start;
            String correlationId = exchange.getResponse().getHeaders()
                    .getFirst(GatewayHeaders.CORRELATION_ID_HEADER);
            Integer status = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value() : null;

            log.info("[{}] {} {} -> {} ({} ms, signal={})",
                    correlationId, request.getMethod(), request.getPath(), status, durationMs, signalType);
        });
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
