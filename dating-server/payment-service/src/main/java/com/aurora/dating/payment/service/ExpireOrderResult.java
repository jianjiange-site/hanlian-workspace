package com.aurora.dating.payment.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ExpireOrderResult {

    private final int code;
    private final String message;
    private final String orderNo;
    private final Long userId;
    private final Integer status;
    private final Boolean expired;
    private final Long closedAtMs;
}
