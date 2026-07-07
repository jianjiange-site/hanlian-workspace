package com.aurora.dating.payment.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CloseExpiredOrdersResult {

    private final int code;
    private final String message;
    private final Integer expireMinutes;
    private final Integer closedCount;
    private final Long expiredBeforeMs;
    private final Long closedAtMs;
}
