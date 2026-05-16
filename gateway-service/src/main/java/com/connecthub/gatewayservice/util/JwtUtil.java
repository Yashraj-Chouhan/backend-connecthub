package com.connecthub.gatewayservice.util;

import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
/**
 * Minimal JWT helper used by the gateway to validate tokens issued by the auth
 * service before requests are routed to downstream services.
 */
public class JwtUtil {

    private final String secret;

    public JwtUtil(@Value("${jwt.secret:connecthub_auth_service_secret_key_32_chars_min}") String secret) {
        this.secret = secret;
    }

    public String extractEmail(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secret.getBytes(StandardCharsets.UTF_8))
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public boolean validateToken(String token) {
        try {
            extractEmail(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }
}
