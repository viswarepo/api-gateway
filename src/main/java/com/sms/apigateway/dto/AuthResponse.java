package com.sms.apigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String tokenType; // "Bearer"
    private long expiresInMs;
    private Long userId;
    private String username;
    private String organizationId;
    //private Set<String> roles;
    private String role;
}
