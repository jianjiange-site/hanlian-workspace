package com.aurora.dating.im.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CallbackService {

    private final ObjectMapper objectMapper;
    private final PresenceService presenceService;

    public CallbackService(ObjectMapper objectMapper, PresenceService presenceService) {
        this.objectMapper = objectMapper;
        this.presenceService = presenceService;
    }

    public CallbackResult handle(String provider, byte[] payload) {
        if (!StringUtils.hasText(provider)) {
            return CallbackResult.reject(4001, "provider must not be blank");
        }
        if (payload == null || payload.length == 0) {
            return CallbackResult.reject(4002, "payload must not be empty");
        }
        if (!"openim".equalsIgnoreCase(provider.trim())) {
            return CallbackResult.allow("unsupported provider ignored");
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String command = firstText(root, "callbackCommand", "command", "event");
            if (!StringUtils.hasText(command)) {
                return CallbackResult.allow("unknown callback allowed");
            }

            if (command.toLowerCase().contains("online")) {
                long userId = readUserId(root);
                presenceService.online(userId, firstText(root, "platform", "terminal"), readEventTime(root));
                return CallbackResult.allow("online recorded");
            }
            if (command.toLowerCase().contains("offline")) {
                long userId = readUserId(root);
                presenceService.offline(userId, readEventTime(root));
                return CallbackResult.allow("offline recorded");
            }

            return CallbackResult.allow("callback allowed");
        } catch (IOException e) {
            return CallbackResult.reject(4003, "payload is not valid json");
        } catch (IllegalArgumentException e) {
            return CallbackResult.reject(4004, e.getMessage());
        }
    }

    private long readUserId(JsonNode root) {
        String value = firstText(root, "userID", "userId", "user_id");
        if (!StringUtils.hasText(value)) {
            JsonNode userNode = root.path("user");
            value = firstText(userNode, "userID", "userId", "user_id");
        }
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("callback user id is missing");
        }
        if (value.startsWith("u_")) {
            value = value.substring(2);
        }
        return Long.parseLong(value);
    }

    private long readEventTime(JsonNode root) {
        JsonNode node = firstNode(root, "eventTimeMs", "event_time_ms", "timestamp", "sendTime");
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0;
        }
        if (node.isNumber()) {
            long value = node.asLong();
            return value < 10_000_000_000L ? value * 1000 : value;
        }
        String text = node.asText();
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        long value = Long.parseLong(text);
        return value < 10_000_000_000L ? value * 1000 : value;
    }

    private String firstText(JsonNode root, String... names) {
        JsonNode node = firstNode(root, names);
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private JsonNode firstNode(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode node = root.path(name);
            if (!node.isMissingNode() && !node.isNull()) {
                return node;
            }
        }
        return null;
    }

    public record CallbackResult(int code, String message, boolean allow) {
        static CallbackResult allow(String message) {
            return new CallbackResult(0, message, true);
        }

        static CallbackResult reject(int code, String message) {
            return new CallbackResult(code, message, false);
        }
    }
}
