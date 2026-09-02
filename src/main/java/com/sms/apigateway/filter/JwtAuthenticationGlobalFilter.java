package com.sms.apigateway.filter;

import com.sms.apigateway.security.GatewayHeaders;
import com.sms.apigateway.security.JwtUtil;
import io.jsonwebtoken.JwtException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * The gateway is the sole authentication and tenant-resolution authority.
 * Callers authenticate via /auth/login and present the resulting JWT as
 * "Authorization: Bearer <token>"; this filter validates it (via the same
 * JwtUtil that mints tokens at login, so claim shape and signing secret are
 * always in sync) and sets X-Organization-Id / X-User-Id / X-User-Roles on
 * the request forwarded downstream, overwriting anything the client sent —
 * a caller cannot forge tenant identity or roles by setting those headers
 * directly. Downstream services trust these headers precisely because, in a
 * real deployment, they'd only be reachable through this gateway (see
 * README "Trust boundary").
 *
 * Missing/invalid/expired tokens short-circuit with 401 before any
 * downstream route is reached.
 */
@Component
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    /** Paths that don't require authentication: ops/health endpoints, circuit-breaker fallbacks, login itself. */
    private static final Set<String> EXEMPT_PREFIXES = Set.of("/actuator", "/fallback", "/auth");

    private final JwtUtil jwtUtil;
    private final GatewayResponseWriter responseWriter;

    public JwtAuthenticationGlobalFilter(JwtUtil jwtUtil, GatewayResponseWriter responseWriter) {
        this.jwtUtil = jwtUtil;
        this.responseWriter = responseWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (EXEMPT_PREFIXES.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return responseWriter.writeError(exchange, HttpStatus.UNAUTHORIZED,
                    "Missing or malformed Authorization header; expected 'Bearer <token>'");
        }

        String token = header.substring(BEARER_PREFIX.length());
        try {
            JwtUtil.AuthenticatedPrincipal principal = jwtUtil.parse(token);

            String roles = principal.roles() == null ? "" : String.join(",", principal.roles());

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .headers(headers -> {
                        headers.remove(GatewayHeaders.ORGANIZATION_ID_HEADER);
                        headers.remove(GatewayHeaders.USER_ID_HEADER);
                        headers.remove(GatewayHeaders.USER_ROLES_HEADER);
                        headers.set(GatewayHeaders.ORGANIZATION_ID_HEADER, principal.organizationId());
                        headers.set(GatewayHeaders.USER_ID_HEADER, principal.username());
                        headers.set(GatewayHeaders.USER_ROLES_HEADER, roles);
                    })
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (JwtException | IllegalArgumentException e) {
            return responseWriter.writeError(exchange, HttpStatus.UNAUTHORIZED,
                    "Invalid or expired token: " + e.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return -50;
    }
}
