package com.sms.apigateway.filter;

import com.sms.apigateway.security.GatewayHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Runs after JwtAuthenticationGlobalFilter, using the X-User-Roles header it
 * set (never a client-supplied one). Enforces:
 *  - reads (GET) on any tenant-facing route: any authenticated caller.
 *  - writes to catalog authoring routes (products/plans/features): ADMIN only.
 *  - the cross-tenant billing batch job: ADMIN only.
 *  - everything else (subscription lifecycle actions - create/cancel/etc.): any authenticated caller.
 * ADMIN implicitly satisfies any requirement, since it's the superset role
 * in this two-role sample.
 */
@Component
public class AuthorizationGlobalFilter implements GlobalFilter, Ordered {

    private static final String ADMIN = "ADMIN";

    private static final Set<String> CATALOG_MANAGEMENT_PREFIXES = Set.of(
            "/api/v1/products/*", "/api/v1/plans/*", "/api/v1/features/*");

    private final GatewayResponseWriter responseWriter;

    public AuthorizationGlobalFilter(GatewayResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requiredRole = requiredRole(request.getPath().value(), request.getMethod());

        if (requiredRole == null) {
            return chain.filter(exchange);
        }

        String rolesHeader = request.getHeaders().getFirst(GatewayHeaders.USER_ROLES_HEADER);
        Set<String> roles = (rolesHeader == null || rolesHeader.isBlank())
                ? Set.of()
                : Set.of(rolesHeader.split(","));

        if (roles.contains(requiredRole) || roles.contains(ADMIN)) {
            return chain.filter(exchange);
        }

        return responseWriter.writeError(exchange, HttpStatus.FORBIDDEN,
                "Role " + requiredRole + " required for " + request.getMethod() + " " + request.getPath());
    }

    /** @return the role required for this call, or null if any authenticated caller may proceed. */
    private String requiredRole(String path, HttpMethod method) {
        boolean isWrite = method != HttpMethod.GET && method != HttpMethod.HEAD && method != HttpMethod.OPTIONS;
        if (!isWrite) {
            return null;
        }
        if (path.equals("/api/v1/subscriptions/process-billing-cycle")) {
            return ADMIN;
        }
        boolean isCatalogManagement = CATALOG_MANAGEMENT_PREFIXES.stream().anyMatch(path::startsWith);
        return isCatalogManagement ? ADMIN : null;
    }

    @Override
    public int getOrder() {
        return -45;
    }
}
