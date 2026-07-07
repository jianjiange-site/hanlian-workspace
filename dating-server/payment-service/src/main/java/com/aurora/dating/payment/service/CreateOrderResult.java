package com.aurora.dating.payment.service;

import lombok.Getter;

@Getter
public class CreateOrderResult {

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

    public CreateOrderResult(int code,
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
                             Integer status) {
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
    }
}
