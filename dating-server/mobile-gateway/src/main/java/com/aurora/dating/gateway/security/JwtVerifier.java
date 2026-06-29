package com.aurora.dating.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

@Component
public class JwtVerifier {

    private final JwtKeyProvider jwtKeyProvider;

    public JwtVerifier(JwtKeyProvider jwtKeyProvider) {
        this.jwtKeyProvider = jwtKeyProvider;
    }

    /**
     * 验证 access token，并解析 userId
     * @param token
     * @return
     */
    public long verifyAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(jwtKeyProvider.getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String tokenType = claims.get("typ", String.class);
        if (!"access".equals(tokenType)) {
            throw new IllegalArgumentException("token type invalid");
        }

        return claims.get("uid", Long.class);
    }

    /**
     * 校验 refresh token，并解析出用户 ID
     * @param token
     * @return
     */
    public long verifyRefreshToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(jwtKeyProvider.getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String tokenType = claims.get("typ", String.class);
        if (!"refresh".equals(tokenType)) {
            throw new IllegalArgumentException("token type invalid");
        }

        return claims.get("uid", Long.class);
    }
}
