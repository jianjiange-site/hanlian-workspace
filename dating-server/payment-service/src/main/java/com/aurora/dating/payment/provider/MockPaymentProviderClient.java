package com.aurora.dating.payment.provider;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class MockPaymentProviderClient implements PaymentProviderClient {

    @Override
    public String provider() {
        return "MOCK";
    }

    @Override
    public PreparePaymentProviderResult preparePayment(PreparePaymentCommand command) {
        OffsetDateTime expireAt = OffsetDateTime.now().plusMinutes(30);
        Long expireAtMs = expireAt.toInstant().toEpochMilli();

        String payUrl = "https://mock-pay.hanlian.local/pay?orderNo=" + command.getOrderNo();
        String providerPayload = "{\"provider\":\"" + escapeJson(command.getProvider())
                + "\",\"orderNo\":\"" + escapeJson(command.getOrderNo())
                + "\",\"amountCents\":" + command.getAmountCents()
                + ",\"currency\":\"" + escapeJson(command.getCurrency())
                + "\",\"returnUrl\":\"" + escapeJson(command.getReturnUrl())
                + "\",\"cancelUrl\":\"" + escapeJson(command.getCancelUrl())
                + "\",\"expireAtMs\":" + expireAtMs
                + "}";

        return new PreparePaymentProviderResult(payUrl, providerPayload, expireAtMs);
    }

    @Override
    public PaymentCallbackProviderResult parsePaymentCallback(PaymentCallbackCommand command) {
        validateCallback(command);

        return new PaymentCallbackProviderResult(
                provider(),
                command.getEventType(),
                command.getOrderNo(),
                command.getProviderTradeNo(),
                command.getPaidAmountCents(),
                command.getCurrency().toUpperCase(),
                command.getRawPayload()
        );
    }

    private void validateCallback(PaymentCallbackCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("callback command must not be null");
        }

        if (command.getOrderNo() == null || command.getOrderNo().isBlank()) {
            throw new IllegalArgumentException("order_no must not be blank");
        }

        if (command.getProviderTradeNo() == null || command.getProviderTradeNo().isBlank()) {
            throw new IllegalArgumentException("provider_trade_no must not be blank");
        }

        if (command.getPaidAmountCents() == null || command.getPaidAmountCents() <= 0) {
            throw new IllegalArgumentException("paid_amount_cents must be positive");
        }

        if (command.getCurrency() == null || command.getCurrency().isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
