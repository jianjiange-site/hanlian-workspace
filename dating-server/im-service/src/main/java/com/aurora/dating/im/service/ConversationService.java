package com.aurora.dating.im.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {

    private final JdbcTemplate jdbcTemplate;

    public ConversationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ConversationResult ensureConversation(long userIdA, long userIdB) {
        if (userIdA <= 0 || userIdB <= 0) {
            throw new IllegalArgumentException("user ids must be positive");
        }
        if (userIdA == userIdB) {
            throw new IllegalArgumentException("conversation requires two different users");
        }

        long minUserId = Math.min(userIdA, userIdB);
        long maxUserId = Math.max(userIdA, userIdB);
        String conversationId = "single_" + minUserId + "_" + maxUserId;

        jdbcTemplate.update("""
                INSERT INTO im_conversation (conversation_id, user_id_a, user_id_b)
                VALUES (?, ?, ?)
                ON CONFLICT (user_id_a, user_id_b) DO UPDATE
                SET updated_at = CURRENT_TIMESTAMP
                """, conversationId, minUserId, maxUserId);

        return new ConversationResult(true, conversationId, "ok");
    }

    public record ConversationResult(boolean success, String conversationId, String message) {
    }
}
