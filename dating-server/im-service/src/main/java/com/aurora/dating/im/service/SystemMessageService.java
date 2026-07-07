package com.aurora.dating.im.service;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SystemMessageService {

    private final JdbcTemplate jdbcTemplate;

    public SystemMessageService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SendResult sendSystemMessage(
            long toUserId,
            String bizType,
            String title,
            String content,
            String payloadJson) {
        if (toUserId <= 0) {
            throw new IllegalArgumentException("to_user_id must be positive");
        }
        if (!StringUtils.hasText(bizType)) {
            throw new IllegalArgumentException("biz_type must not be blank");
        }

        String messageId = "sys_" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update("""
                INSERT INTO im_system_message (message_id, to_user_id, biz_type, title, content, payload_json, provider)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, messageId, toUserId, bizType.trim(), title, content, payloadJson, "mock");

        return new SendResult(true, messageId, "mock system message saved", true);
    }

    public record SendResult(boolean success, String messageId, String message, boolean mock) {
    }
}
