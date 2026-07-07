package com.aurora.dating.payment.service;

import lombok.Getter;

@Getter
public class GetOrderResult {

    private final int code;
    private final String message;
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
    private final Long createdAtMs;
    private final Long updatedAtMs;

    public GetOrderResult(int code,
                          String message,
                          String orderNo,
                          Long userId,
                          String productType,
                          String productCode,
                          String provider,
                          Long amountCents,
                          String currency,
                          Long coinAmount,
                          String subscriptionTier,
                          Integer status,
                          String providerTradeNo,
                          Long paidAtMs,
                          Long createdAtMs,
                          Long updatedAtMs) {
        this.code = code;
        this.message = message;
        this.orderNo = orderNo;
        this.userId = userId;
        this.productType = productType;
        this.productCode = productCode;
        this.provider = provider;
        this.amountCents = amountCents;
        this.currency = currency;
        this.coinAmount = coinAmount;
        this.subscriptionTier = subscriptionTier;
        this.status = status;
        this.providerTradeNo = providerTradeNo;
        this.paidAtMs = paidAtMs;
        this.createdAtMs = createdAtMs;
        this.updatedAtMs = updatedAtMs;
    }
}
