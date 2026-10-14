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
    private String username;
    private String password;
    private String email;
    private String fullName;
    private String role;
    private String organizationId;
    private String organizationName;
    private String customerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
