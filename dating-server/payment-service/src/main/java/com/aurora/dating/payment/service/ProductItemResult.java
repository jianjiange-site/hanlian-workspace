package com.aurora.dating.payment.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductItemResult {

    private final String productCode;
    private final String productType;
    private final String title;
    private final Long amountCents;
    private final String currency;
    private final Long coinAmount;
    private final String subscriptionTier;
    private final Integer subscriptionDays;
}
