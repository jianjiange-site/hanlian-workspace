package com.aurora.dating.payment.provider;

public interface PaymentProviderClient {

    String provider();

    PreparePaymentProviderResult preparePayment(PreparePaymentCommand command);

    PaymentCallbackProviderResult parsePaymentCallback(PaymentCallbackCommand command);
}
