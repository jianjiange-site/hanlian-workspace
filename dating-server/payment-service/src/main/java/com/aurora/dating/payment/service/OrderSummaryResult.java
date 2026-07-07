package com.aurora.dating.payment.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderSummaryResult {

    private final String orderNo;
    private final Long userId;
    private final String productType;
    private final String productCode;
    private final String provider;
    private final Long amountCents;
    private final String currency;
    private final Long coinAmount;
    private final String subscriptionTier;
    private final Integer status;
    private final String providerTradeNo;
    private final Long paidAtMs;
    private final Long closedAtMs;
    private final Long createdAtMs;
    private final Long updatedAtMs;
}
