package com.sms.apigateway.security;

public final class GatewayHeaders {

    private GatewayHeaders() {
    }

    public static final String CORRELATION_ID_HEADER = "X-Request-Id";

    /** Set by JwtAuthenticationGlobalFilter after validating the token; never trust one sent by the client. */
    public static final String ORGANIZATION_ID_HEADER = "X-Organization-Id";

    public static final String USER_ID_HEADER = "X-User-Id";

    public static final String USER_ROLES_HEADER = "X-User-Roles";
}
