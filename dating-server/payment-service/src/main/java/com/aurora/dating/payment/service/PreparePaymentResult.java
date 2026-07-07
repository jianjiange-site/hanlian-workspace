package com.aurora.dating.payment.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PreparePaymentResult {

    private final int code;
    private final String message;
    private final String orderNo;
    private final Long userId;
    private final String productType;
    private final String productCode;
    private final String provider;
    private final Long amountCents;
    private final String currency;
    private final Integer status;
    private final String payUrl;
    private final String providerPayload;
    private final Long expireAtMs;
}