package com.sms.apigateway.service;


import com.sms.apigateway.client.UserServiceClient;
import com.sms.apigateway.dto.*;
import com.sms.apigateway.dto.LoginRequest;
import com.sms.apigateway.dto.RegisterRequest;
import com.sms.apigateway.exception.InvalidCredentialsException;
import com.sms.apigateway.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserServiceClient userServiceClient;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

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
                .role(credentials.getRole())
                .build();
    }

    /** Registration is delegated to user-service, which owns user storage and password hashing. */
    public UserDto register(@Valid RegisterRequest request) {
        return userServiceClient.register(request);
    }
}
