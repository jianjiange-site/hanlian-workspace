package com.aurora.dating.post.config;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 雪花算法生成id（12位时间戳+12位自增号）
 */
@Component
public class SnowflakeIdGenerator {

    private final AtomicLong sequence = new AtomicLong(0);

    public long nextId() {
        long timestamp = System.currentTimeMillis();
        long seq = sequence.getAndIncrement() & 4095;
        return (timestamp << 12) | seq;
    }
}