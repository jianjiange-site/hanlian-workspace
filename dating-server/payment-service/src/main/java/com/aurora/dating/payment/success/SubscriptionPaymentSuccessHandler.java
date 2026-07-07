package com.aurora.dating.payment.success;

import com.aurora.dating.payment.entity.PaymentOrderEntity;
import com.aurora.dating.payment.service.PaySuccessResult;
import com.aurora.dating.payment.service.PaymentCoinService;
import com.aurora.dating.payment.service.PaymentSubscriptionService;
import com.aurora.dating.payment.service.SubscriptionResult;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionPaymentSuccessHandler implements PaymentSuccessHandler {

    private static final int STATUS_PAID = 20;

    private final PaymentSubscriptionService paymentSubscriptionService;

    public SubscriptionPaymentSuccessHandler(PaymentSubscriptionService paymentSubscriptionService) {
        this.paymentSubscriptionService = paymentSubscriptionService;
    }

    @Override
    public String productType() {
        return "SUBSCRIPTION";
    }

    @Override
    public PaySuccessResult finishPaidOrder(PaymentOrderEntity order) {
        if (order.getSubscriptionTier() == null || order.getSubscriptionTier().isBlank()) {
            throw new IllegalArgumentException("subscription_tier must not be blank");
        }

        SubscriptionResult subscriptionResult = paymentSubscriptionService.setSubscription(
                order.getUserId(),
                order.getSubscriptionTier(),
                subscriptionDays(order.getSubscriptionTier())
        );

        return toResult(order, "ok", subscriptionResult);
    }

    @Override
    public PaySuccessResult alreadyPaid(PaymentOrderEntity order) {
        SubscriptionResult subscriptionResult = paymentSubscriptionService.getSubscription(order.getUserId());
        return toResult(order, "already paid", subscriptionResult);
    }

    private PaySuccessResult toResult(PaymentOrderEntity order, String message, SubscriptionResult subscriptionResult) {
        return new PaySuccessResult(
                PaymentCoinService.CODE_OK,
                message,
                order.getOrderNo(),
                order.getUserId(),
                order.getProductType(),
                order.getProductCode(),
                order.getProviderTradeNo(),
                STATUS_PAID,
                0L,
                "",
                subscriptionResult.getTier(),
                subscriptionResult.getActive(),
                subscriptionResult.getExpireAtMs()
        );
    }

    private Integer subscriptionDays(String subscriptionTier) {
        if ("WEEKLY".equals(subscriptionTier)) {
            return 7;
        }

        if ("MONTHLY".equals(subscriptionTier)) {
            return 30;
        }

        if ("YEARLY".equals(subscriptionTier)) {
            return 365;
        }

        throw new IllegalArgumentException("unsupported subscription_tier");
    }
}
