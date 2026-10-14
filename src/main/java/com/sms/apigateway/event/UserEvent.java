package com.sms.apigateway.event;

public record UserEvent (
    String username,
    String email,
    String fullName,
    String organizationId
){
    public static UserEvent of(
            String username,
            String email,
            String fullName,
            String organizationId
    ) {
        return new UserEvent(
            username,
                email,
                fullName,
                organizationId
        );
    }
}
