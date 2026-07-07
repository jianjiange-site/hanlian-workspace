package com.aurora.dating.im.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PresenceService {

    private static final int DEFAULT_LIMIT = 5000;
    private static final int MAX_LIMIT = 50000;

    private final StringRedisTemplate stringRedisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final String onlineKey;

    public PresenceService(
            StringRedisTemplate stringRedisTemplate,
            JdbcTemplate jdbcTemplate,
            @Value("${app.cache.key-prefix:hanlian}") String keyPrefix) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.onlineKey = keyPrefix + ":im:presence:online";
    }

    public void online(long userId, String platform, long onlineAtMs) {
        validateUserId(userId);
        long eventTimeMs = normalizeEventTime(onlineAtMs);
        Boolean added = stringRedisTemplate.opsForZSet()
                .addIfAbsent(onlineKey, String.valueOf(userId), eventTimeMs);
        if (Boolean.TRUE.equals(added)) {
            jdbcTemplate.update("""
                    INSERT INTO user_online_session (user_id, platform, online_at)
                    VALUES (?, ?, ?)
                    """, userId, normalizePlatform(platform), Timestamp.from(Instant.ofEpochMilli(eventTimeMs)));
        }
    }

    public void offline(long userId, long offlineAtMs) {
        validateUserId(userId);
        long eventTimeMs = normalizeEventTime(offlineAtMs);
        Double onlineScore = stringRedisTemplate.opsForZSet().score(onlineKey, String.valueOf(userId));
        if (onlineScore == null) {
            return;
        }

        long onlineAtMs = onlineScore.longValue();
        long durationSeconds = Math.max(0, (eventTimeMs - onlineAtMs) / 1000);
        jdbcTemplate.update("""
                UPDATE user_online_session
                SET offline_at = ?,
                    duration_seconds = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = (
                    SELECT id
                    FROM user_online_session
                    WHERE user_id = ?
                      AND offline_at IS NULL
                      AND deleted = FALSE
                    ORDER BY online_at DESC
                    LIMIT 1
                )
                """, Timestamp.from(Instant.ofEpochMilli(eventTimeMs)), durationSeconds, userId);
        stringRedisTemplate.opsForZSet().remove(onlineKey, String.valueOf(userId));
    }

    public List<Long> listOnlineUserIds(long sinceMs, long untilMs, int limit) {
        long[] window = normalizeWindow(sinceMs, untilMs);
        return Objects.requireNonNullElse(
                        stringRedisTemplate.opsForZSet()
                                .rangeByScore(onlineKey, window[0], window[1], 0, normalizeLimit(limit)),
                        List.<String>of())
                .stream()
                .map(this::parseUserId)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<Long> listRecentOfflineUsers(long sinceMs, long untilMs, int limit) {
        long[] window = normalizeWindow(sinceMs, untilMs);
        return jdbcTemplate.queryForList("""
                        SELECT DISTINCT user_id
                        FROM user_online_session
                        WHERE offline_at >= ?
                          AND offline_at < ?
                          AND deleted = FALSE
                        ORDER BY user_id
                        LIMIT ?
                        """,
                Long.class,
                Timestamp.from(Instant.ofEpochMilli(window[0])),
                Timestamp.from(Instant.ofEpochMilli(window[1])),
                normalizeLimit(limit));
    }

    public String onlineKey() {
        return onlineKey;
    }

    private long normalizeEventTime(long eventTimeMs) {
        if (eventTimeMs > 0) {
            return eventTimeMs;
        }
        return Instant.now().toEpochMilli();
    }

    private long[] normalizeWindow(long sinceMs, long untilMs) {
        long until = untilMs > 0 ? untilMs : Instant.now().toEpochMilli();
        long since = sinceMs > 0 ? sinceMs : 0;
        if (since > until) {
            throw new IllegalArgumentException("since_ms must be less than or equal to until_ms");
        }
        return new long[]{since, until};
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private Long parseUserId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
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
}
