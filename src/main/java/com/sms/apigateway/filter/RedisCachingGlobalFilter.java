package com.sms.apigateway.filter;

import com.sms.apigateway.cache.RedisCatalogCacheService;
import com.sms.apigateway.security.GatewayHeaders;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Caches successful GET responses for catalog read endpoints in Redis, keyed
 * per tenant + path + query string, and invalidates a tenant's cached reads
 * whenever a write hits one of the same resource types.
 *
 * This is a simplified, non-streaming implementation: it buffers the full
 * response body in memory to cache it (via a ServerHttpResponseDecorator),
 * which is fine for the small JSON payloads these services return but would
 * need revisiting for large or genuinely streamed responses.
 */
@Component
public class RedisCachingGlobalFilter implements GlobalFilter, Ordered {

    private static final Set<String> CACHEABLE_PREFIXES = Set.of(
            "/api/v1/catalog", "/api/v1/products", "/api/v1/plans", "/api/v1/features");
    private static final String CACHE_STATUS_HEADER = "X-Cache";

    private final RedisCatalogCacheService cacheService;

    public RedisCachingGlobalFilter(RedisCatalogCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        if (!isCacheablePath(path)) {
            return chain.filter(exchange);
        }

        String organizationId = request.getHeaders().getFirst(GatewayHeaders.ORGANIZATION_ID_HEADER);
        if (organizationId == null) {
            return chain.filter(exchange);
        }

        if (request.getMethod() != HttpMethod.GET) {
            // A write to a cacheable resource type invalidates that tenant's cached reads once it succeeds.
            return chain.filter(exchange).then(Mono.defer(() -> invalidateIfSuccessful(exchange, organizationId)));
        }

        String cacheKey = buildCacheKey(organizationId, request);
        return cacheService.get(cacheKey)
                .flatMap(cachedJson -> writeCachedResponse(exchange, cachedJson))
                .switchIfEmpty(Mono.defer(() -> proceedAndCache(exchange, chain, organizationId, cacheKey)));
    }

    private boolean isCacheablePath(String path) {
        return CACHEABLE_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private String buildCacheKey(String organizationId, ServerHttpRequest request) {
        String query = request.getURI().getRawQuery();
        return "cache:" + organizationId + ":" + request.getPath().value() + (query != null ? "?" + query : "");
    }

    private Mono<Void> writeCachedResponse(ServerWebExchange exchange, String cachedJson) {
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().add(CACHE_STATUS_HEADER, "HIT");
        DataBuffer buffer = response.bufferFactory().wrap(cachedJson.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private Mono<Void> proceedAndCache(ServerWebExchange exchange, GatewayFilterChain chain,
                                        String organizationId, String cacheKey) {
        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();

        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (getStatusCode() != null && getStatusCode().is2xxSuccessful()) {
                    getHeaders().add(CACHE_STATUS_HEADER, "MISS");
                    Flux<? extends DataBuffer> fluxBody = Flux.from(body);
                    return super.writeWith(fluxBody.collectList().flatMapMany(buffers -> {
                        DataBuffer joined = bufferFactory.join(buffers);
                        byte[] content = new byte[joined.readableByteCount()];
                        joined.read(content);
                        DataBufferUtils.release(joined);
                        String json = new String(content, StandardCharsets.UTF_8);
                        cacheService.put(organizationId, cacheKey, json).subscribe();
                        return Flux.just(bufferFactory.wrap(content));
                    }));
                }
                return super.writeWith(body);
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    private Mono<Void> invalidateIfSuccessful(ServerWebExchange exchange, String organizationId) {
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        if (status != null && status.is2xxSuccessful()) {
            return cacheService.invalidate(organizationId);
        }
        return Mono.empty();
    }

    @Override
    public int getOrder() {
        return -30;
    }
}
