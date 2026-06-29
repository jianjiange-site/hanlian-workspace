package com.aurora.dating.gateway.security;

import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class JwtKeyProvider {

    private static final String DEV_SECRET = "hanlian-mobile-gateway-dev-secret-must-be-at-least-32-bytes";

    private final SecretKey secretKey = Keys.hmacShaKeyFor(DEV_SECRET.getBytes(StandardCharsets.UTF_8));

    public SecretKey getSecretKey() {
        return secretKey;
    }
}
