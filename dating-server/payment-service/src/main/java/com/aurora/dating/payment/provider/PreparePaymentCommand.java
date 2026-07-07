package com.aurora.dating.payment.provider;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PreparePaymentCommand {

    private final String orderNo;
    private final Long userId;
    private final String productType;
    private final String productCode;
    private final String provider;
    private final Long amountCents;
    private final String currency;
    private final String returnUrl;
    private final String cancelUrl;
}
