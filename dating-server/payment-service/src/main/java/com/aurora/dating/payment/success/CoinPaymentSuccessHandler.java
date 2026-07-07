package com.aurora.dating.payment.success;

import com.aurora.dating.payment.entity.PaymentOrderEntity;
import com.aurora.dating.payment.service.GrantCoinsResult;
import com.aurora.dating.payment.service.PaySuccessResult;
import com.aurora.dating.payment.service.PaymentCoinService;
import org.springframework.stereotype.Component;

@Component
public class CoinPaymentSuccessHandler implements PaymentSuccessHandler {

    private static final int STATUS_PAID = 20;

    private final PaymentCoinService paymentCoinService;

    public CoinPaymentSuccessHandler(PaymentCoinService paymentCoinService) {
        this.paymentCoinService = paymentCoinService;
    }

    @Override
    public String productType() {
        return "COIN";
    }

    @Override
    public PaySuccessResult finishPaidOrder(PaymentOrderEntity order) {
        if (order.getCoinAmount() == null || order.getCoinAmount() <= 0) {
            throw new IllegalArgumentException("coin_amount must be positive");
        }

        GrantCoinsResult grantResult = paymentCoinService.grantCoins(
                order.getUserId(),
                order.getCoinAmount(),
                "PAYMENT_ORDER",
                "payment-order:" + order.getOrderNo()
        );

        return new PaySuccessResult(
                PaymentCoinService.CODE_OK,
                "ok",
                order.getOrderNo(),
                order.getUserId(),
                order.getProductType(),
                order.getProductCode(),
                order.getProviderTradeNo(),
                STATUS_PAID,
                grantResult.getBalance(),
                grantResult.getLedgerNo(),
                "",
                false,
                0L
        );
    }

    @Override
    public PaySuccessResult alreadyPaid(PaymentOrderEntity order) {
        return new PaySuccessResult(
                PaymentCoinService.CODE_OK,
                "already paid",
                order.getOrderNo(),
                order.getUserId(),
                order.getProductType(),
                order.getProductCode(),
                order.getProviderTradeNo(),
                STATUS_PAID,
                paymentCoinService.getCoins(order.getUserId()).getBalance(),
                "",
                "",
                false,
                0L
        );
    }
}
