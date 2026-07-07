package com.aurora.dating.payment.provider;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentCallbackProviderResult {

    private final String provider;
    private final String eventType;
    private final String orderNo;
    private final String providerTradeNo;
    private final Long paidAmountCents;
    private final String currency;
    private final String rawPayload;
}
