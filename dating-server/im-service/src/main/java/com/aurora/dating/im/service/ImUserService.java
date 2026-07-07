package com.aurora.dating.im.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ImUserService {

    private final JdbcTemplate jdbcTemplate;
    private final long tokenTtlSeconds;

    public ImUserService(
            JdbcTemplate jdbcTemplate,
            @Value("${im.token.ttl-seconds:604800}") long tokenTtlSeconds) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    public RegisterResult register(long userId, String nickname, String avatar) {
        validateUserId(userId);
        String imUserId = imUserId(userId);
        String displayName = StringUtils.hasText(nickname) ? nickname.trim() : imUserId;

        jdbcTemplate.update("""
                INSERT INTO im_registered_user (user_id, im_user_id, nickname, avatar)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE
                SET nickname = EXCLUDED.nickname,
                    avatar = EXCLUDED.avatar,
                    updated_at = CURRENT_TIMESTAMP
                """, userId, imUserId, displayName, avatar);

        return new RegisterResult(true, imUserId, "registered");
    }

    public TokenResult getToken(long userId, String platform) {
        RegisterResult registerResult = register(userId, null, null);
        long expireAtMs = Instant.now().plusSeconds(tokenTtlSeconds).toEpochMilli();
        String seed = registerResult.imUserId() + ":" + normalizePlatform(platform) + ":" + expireAtMs;
        String token = "mock-im-token-" + UUID.nameUUIDFromBytes(seed.getBytes()).toString().replace("-", "");
        return new TokenResult(registerResult.imUserId(), token, expireAtMs, true);
    }

    public String imUserId(long userId) {
        validateUserId(userId);
        return "u_" + userId;
    }

    private void validateUserId(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("user_id must be positive");
        }
    }

    private String normalizePlatform(String platform) {
        if (!StringUtils.hasText(platform)) {
            return "unknown";
        }
        return platform.trim().toLowerCase();
    }

    public record RegisterResult(boolean success, String imUserId, String message) {
    }

    public record TokenResult(String imUserId, String token, long expireAtMs, boolean mock) {
    }
}
