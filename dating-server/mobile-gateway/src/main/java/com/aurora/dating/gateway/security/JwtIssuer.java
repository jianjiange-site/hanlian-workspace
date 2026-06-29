package com.aurora.dating.gateway.security;

import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtIssuer {

    private final JwtKeyProvider jwtKeyProvider;
    private final JwtTokenProperties jwtTokenProperties;

    public JwtIssuer(JwtKeyProvider jwtKeyProvider, JwtTokenProperties jwtTokenProperties) {
        this.jwtKeyProvider = jwtKeyProvider;
        this.jwtTokenProperties = jwtTokenProperties;
    }

    /**
     * 给指定用户签发一组 token
     * @param userId
     * @return
     */
    public TokenPair issue(long userId) {
        Instant now = Instant.now();
        long accessTokenSeconds = jwtTokenProperties.getAccessTokenSeconds();
        long refreshTokenSeconds = jwtTokenProperties.getRefreshTokenSeconds();

        String accessToken = buildToken(userId, "access", now, now.plusSeconds(accessTokenSeconds));
        String refreshToken = buildToken(userId, "refresh", now, now.plusSeconds(refreshTokenSeconds));

        return new TokenPair(accessToken, refreshToken, accessTokenSeconds, refreshTokenSeconds);
    }

    /**
     * 构造一个具体 JWT 字符串
     * @param userId
     * @param tokenType
     * @param issuedAt
     * @param expiresAt
     * @return
     */
    private String buildToken(long userId, String tokenType, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .id(UUID.randomUUID().toString())
                .claim("typ", tokenType)
                .claim("uid", userId)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(jwtKeyProvider.getSecretKey())
                .compact();
    }
}
