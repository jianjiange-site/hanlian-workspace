package com.aurora.dating.payment.service;

import lombok.Getter;

@Getter
public class SubscriptionResult {

    private final int code;
    private final String message;
    private final Long userId;
    private final String tier;
    private final Boolean active;
    private final Long expireAtMs;
    private final Integer dailyRightSwipeLimit;
    private final Integer dailyCardLimit;
    private final Integer dailySuperHiLimit;

    public SubscriptionResult(int code,
                              String message,
                              Long userId,
                              String tier,
                              Boolean active,
                              Long expireAtMs,
                              Integer dailyRightSwipeLimit,
                              Integer dailyCardLimit,
                              Integer dailySuperHiLimit) {
        this.code = code;
        this.message = message;
        this.userId = userId;
        this.tier = tier;
        this.active = active;
        this.expireAtMs = expireAtMs;
        this.dailyRightSwipeLimit = dailyRightSwipeLimit;
        this.dailyCardLimit = dailyCardLimit;
        this.dailySuperHiLimit = dailySuperHiLimit;
    }
}
