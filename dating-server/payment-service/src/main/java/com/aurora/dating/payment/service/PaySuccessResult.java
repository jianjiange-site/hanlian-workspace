package com.aurora.dating.payment.service;

import lombok.Getter;

@Getter
public class PaySuccessResult {

    private final int code;
    private final String message;
    private final String orderNo;
    private final Long userId;
    private final String productType;
    private final String productCode;
    private final String providerTradeNo;
    private final Integer status;
    private final Long balance;
    private final String ledgerNo;
    private final String tier;
    private final Boolean active;
    private final Long expireAtMs;

    public PaySuccessResult(int code,
                            String message,
                            String orderNo,
                            Long userId,
                            String productType,
                            String productCode,
                            String providerTradeNo,
                            Integer status,
                            Long balance,
                            String ledgerNo,
                            String tier,
                            Boolean active,
                            Long expireAtMs) {
        this.code = code;
        this.message = message;
        this.orderNo = orderNo;
        this.userId = userId;
        this.productType = productType;
        this.productCode = productCode;
        this.providerTradeNo = providerTradeNo;
        this.status = status;
        this.balance = balance;
        this.ledgerNo = ledgerNo;
        this.tier = tier;
        this.active = active;
        this.expireAtMs = expireAtMs;
    }
}
