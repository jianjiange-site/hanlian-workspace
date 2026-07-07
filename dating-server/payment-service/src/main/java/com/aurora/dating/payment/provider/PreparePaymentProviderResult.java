package com.aurora.dating.payment.provider;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PreparePaymentProviderResult {

    private final String payUrl;
    private final String providerPayload;
    private final Long expireAtMs;
}
