package com.aurora.dating.payment.error;

public final class PaymentErrorCode {

    public static final int OK = 0;
    public static final int INSUFFICIENT_COINS = 3001;
    public static final int BAD_REQUEST = 4000;
    public static final int INTERNAL_ERROR = 5000;

    private PaymentErrorCode() {
    }
}
