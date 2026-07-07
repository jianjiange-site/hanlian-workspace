package com.aurora.dating.payment.success;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PaymentSuccessHandlerRouter {

    private final Map<String, PaymentSuccessHandler> handlers;

    public PaymentSuccessHandlerRouter(List<PaymentSuccessHandler> handlers) {
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(
                        handler -> handler.productType().toUpperCase(),
                        Function.identity()
                ));
    }

    public PaymentSuccessHandler getHandler(String productType) {
        if (productType == null || productType.isBlank()) {
            throw new IllegalArgumentException("product_type must not be blank");
        }

        PaymentSuccessHandler handler = handlers.get(productType.toUpperCase());
        if (handler == null) {
            throw new IllegalArgumentException("unsupported product_type");
        }

        return handler;
    }
}
