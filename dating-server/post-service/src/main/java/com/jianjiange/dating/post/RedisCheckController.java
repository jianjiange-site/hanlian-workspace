package com.jianjiange.dating.post;

import java.time.Duration;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedisCheckController {

    private static final String CHECK_KEY = "hanlian:post-service:check:redis";
    private static final String CHECK_VALUE = "ok";
    private static final Duration CHECK_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate stringRedisTemplate;

    public RedisCheckController(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @GetMapping("/internal/check/redis")
    public Map<String, String> checkRedis() {
        stringRedisTemplate.opsForValue().set(CHECK_KEY, CHECK_VALUE, CHECK_TTL);

        String value = stringRedisTemplate.opsForValue().get(CHECK_KEY);
        Long ttlSeconds = stringRedisTemplate.getExpire(CHECK_KEY);

        return Map.of(
                "redis", "ok",
                "key", CHECK_KEY,
                "value", String.valueOf(value),
                "ttlSeconds", String.valueOf(ttlSeconds)
        );
    }
}
