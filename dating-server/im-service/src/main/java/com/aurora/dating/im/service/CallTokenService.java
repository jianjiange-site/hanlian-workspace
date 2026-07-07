package com.aurora.dating.im.service;

import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CallTokenService {

    private final long ttlSeconds;

    public CallTokenService(@Value("${im.call.ttl-seconds:1800}") long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public CallTokenResult generate(long userId, long peerUserId) {
        if (userId <= 0 || peerUserId <= 0) {
            throw new IllegalArgumentException("user ids must be positive");
        }
        if (userId == peerUserId) {
            throw new IllegalArgumentException("call requires two different users");
        }

        long minUserId = Math.min(userId, peerUserId);
        long maxUserId = Math.max(userId, peerUserId);
        String roomId = "call_" + minUserId + "_" + maxUserId + "_" + UUID.randomUUID().toString().replace("-", "");
        long expireAtMs = Instant.now().plusSeconds(ttlSeconds).toEpochMilli();
        String token = "mock-call-token-" + UUID.nameUUIDFromBytes((roomId + ":" + userId).getBytes())
                .toString()
                .replace("-", "");

        return new CallTokenResult(roomId, token, expireAtMs, true);
    }

    public record CallTokenResult(String roomId, String token, long expireAtMs, boolean mock) {
    }
}
