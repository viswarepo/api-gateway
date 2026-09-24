package com.sms.apigateway.service;


import com.sms.apigateway.client.UserServiceClient;
import com.sms.apigateway.dto.*;
import com.sms.apigateway.dto.LoginRequest;
import com.sms.apigateway.dto.RegisterRequest;
import com.sms.apigateway.exception.InvalidCredentialsException;
import com.sms.apigateway.security.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserServiceClient userServiceClient;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenDenylistService tokenDenylistService;
    @Value("${portal.default-redirect-url:/}")
    private String defaultRedirectUrl;
    @Value("${portal.admin-redirect-url:/admin/admindashboard}")
    private String adminRedirectUrl;

    @Value("${portal.customer-redirect-url:/customer/customerdashboard}")
    private String customerRedirectUrl;

    public AuthResponse login(@Valid LoginRequest request) {
        // 1. Fetch the user's stored credentials from user-service.
        UserResponseDTO credentials;
        try {
            credentials = userServiceClient.getCredentials(request.getUsername());
        } catch (InvalidCredentialsException e) {
            // 404 from user-service was already translated to this by UserServiceClient.
            log.warn("Login failed - unknown username: {}", request.getUsername());
            throw e;
        }

        // 2. Reject disabled/locked accounts before even checking the password.
        /*if (!credentials.isEnabled()) {
            throw new AccountDisabledException("This account has been disabled");
        }
        if (credentials.isAccountLocked()) {
            throw new AccountDisabledException("This account is locked");
        }*/

        // 3. Compare the submitted plaintext password against the BCrypt hash
        //    returned by user-service. The hash never leaves user-service's DB
        //    in plaintext, and auth-service never stores it.
        if (!passwordEncoder.matches(request.getPassword(), credentials.getPassword())) {
            log.warn("Login failed - bad password for username: {}", request.getUsername());
            throw new InvalidCredentialsException("Invalid username or password");
        }

        // 4. Credentials check out -> mint a JWT carrying identity, tenant, and roles.
        java.util.List<String> roles = credentials.getRole() != null
                ? java.util.List.of(credentials.getRole())
                : java.util.List.of();
        String token = jwtUtil.generateToken(
                credentials.getId(), credentials.getUsername(), credentials.getOrganizationId(), roles);

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresInMs(jwtUtil.getExpirationMs())
                .userId(credentials.getId())
                .username(credentials.getUsername())
                .organizationId(credentials.getOrganizationId())
                .customerId(credentials.getCustomerId())
                .role(credentials.getRole())
                .email(credentials.getEmail())
                .build();
    }

    /** Registration is delegated to user-service, which owns user storage and password hashing. */
    /*public UserDto register(@Valid RegisterRequest request) {
        return userServiceClient.register(request);
    }*/

    /**
     * Revokes the presented access token immediately, via the Redis
     * denylist, rather than waiting for it to expire naturally.
     */
    public void logout(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidCredentialsException("Missing or malformed Authorization header");
        }
        String token = authHeader.substring("Bearer ".length());

        java.util.Date expiration;
        try {
            expiration = jwtUtil.parseAndValidate(token).getExpiration();
        } catch (JwtException | IllegalArgumentException e) {
            // Already invalid/expired - nothing to revoke, but don't leak
            // *why* via a different status than a normal auth failure.
            throw new InvalidCredentialsException("Invalid or expired token");
        }

        tokenDenylistService.revoke(token, expiration).block();
        log.info("Token revoked (logout)");
    }
    private String redirectUrlForRole(String role) {
        if (role == null) {
            log.warn("Login succeeded but user has no role set; using default redirect");
            return defaultRedirectUrl;
        }
        // role here is ERole.toString() from user-service, e.g. "ROLE_ADMIN",
        // "ROLE_CUSTOMER" - not the bare "ADMIN"/"CUSTOMER".
        return switch (role.toUpperCase(Locale.ROOT)) {
            case "ROLE_ADMIN" -> adminRedirectUrl;
            case "ROLE_CUSTOMER" -> customerRedirectUrl;
            default -> {
                log.warn("Login succeeded for unrecognized role '{}'; using default redirect", role);
                yield defaultRedirectUrl;
            }
        };
    }

    /** Registration is delegated to user-service, which owns user storage and password hashing. */
    public UserDto register(@Valid RegisterRequest request) {
        return userServiceClient.register(request);
    }

}
