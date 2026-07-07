package com.aurora.dating.payment.success;

import com.aurora.dating.payment.entity.PaymentOrderEntity;
import com.aurora.dating.payment.service.PaySuccessResult;

public interface PaymentSuccessHandler {

    String productType();

    PaySuccessResult finishPaidOrder(PaymentOrderEntity order);

    PaySuccessResult alreadyPaid(PaymentOrderEntity order);
}
