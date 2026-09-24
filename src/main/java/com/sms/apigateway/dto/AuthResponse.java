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
    private String userId;
    private String username;
    private String organizationId;
    private String customerId;
    //private Set<String> roles;
    private String role;
    private String email;
    private String redirectUrl;

}
