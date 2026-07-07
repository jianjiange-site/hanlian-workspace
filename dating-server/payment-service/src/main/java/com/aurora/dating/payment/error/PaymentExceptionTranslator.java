package com.aurora.dating.payment.error;

public final class PaymentExceptionTranslator {

    private PaymentExceptionTranslator() {
    }

    public static PaymentErrorResult translate(RuntimeException e) {
        if (e instanceof IllegalArgumentException) {
            return new PaymentErrorResult(PaymentErrorCode.BAD_REQUEST, safeMessage(e));
        }

        return new PaymentErrorResult(PaymentErrorCode.INTERNAL_ERROR, safeMessage(e));
    }

    private static String safeMessage(RuntimeException e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return message;
    }
}
