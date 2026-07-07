package com.aurora.dating.payment.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentErrorResult {

    private final int code;
    private final String message;
}
