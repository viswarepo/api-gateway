package com.sms.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * Issues and verifies the JWTs used across the whole gateway: AuthController
 * mints them at login, JwtAuthenticationGlobalFilter verifies them on every
 * subsequently proxied request. Both sides now share this single class, one
 * secret property (jwt.secret), and one claim shape (userId, org, roles) —
 * previously login used this class while verification used a separate
 * JwtService with a different secret key and different claims, so a token
 * minted at login didn't carry what verification required.
 */
@Component
public class JwtUtil {

    private static final String ORG_CLAIM = "org";
    private static final String ROLES_CLAIM = "roles";
    private static final String USER_ID_CLAIM = "userId";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String userId, String username, String organizationId, List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(username)
                .claim(USER_ID_CLAIM, userId)
                .claim(ORG_CLAIM, organizationId)
                .claim(ROLES_CLAIM, roles)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    /** @throws io.jsonwebtoken.JwtException if the token is malformed, expired, or fails signature verification. */
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Convenience for JwtAuthenticationGlobalFilter: parses once and returns everything the gateway headers need. */
    public AuthenticatedPrincipal parse(String token) {
        Claims claims = parseAndValidate(token);
        String organizationId = claims.get(ORG_CLAIM, String.class);
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get(ROLES_CLAIM, List.class);
        return new AuthenticatedPrincipal(claims.getSubject(), organizationId, roles);
    }

    public boolean isValid(String token) {
        try {
            parseAndValidate(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public record AuthenticatedPrincipal(String username, String organizationId, List<String> roles) {
    }
}
