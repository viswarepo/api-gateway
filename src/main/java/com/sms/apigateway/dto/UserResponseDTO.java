package com.sms.apigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {
    private String id;
    private String organizationId;
    private String username;
    private String password;
    private String email;
    private String firstName;
    private String lastName;
    private String customerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    //private Set<String> roles;
    private String role;
}
