package com.aurora.dating.payment.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CallbackRecordResult {

    private final Long id;
    private final String provider;
    private final String eventType;
    private final String orderNo;
    private final String providerTradeNo;
    private final Long paidAmountCents;
    private final String currency;
    private final String rawPayload;
    private final Integer processStatus;
    private final String errorMessage;
    private final Long createdAtMs;
}
