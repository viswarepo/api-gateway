package com.sms.apigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Mirrors user-service's UserCredentialsResponse shape so RestTemplate/Jackson
 * can deserialize the internal credentials lookup response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCredentialsResponse {
    private String id;
    private String username;
    private String passwordHash;
    private Set<String> roles;
    private boolean enabled;
    private boolean accountLocked;
}
