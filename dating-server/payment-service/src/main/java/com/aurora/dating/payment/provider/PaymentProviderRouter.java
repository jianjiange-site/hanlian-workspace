package com.aurora.dating.payment.provider;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PaymentProviderRouter {

    private final Map<String, PaymentProviderClient> clients;

    public PaymentProviderRouter(List<PaymentProviderClient> clients) {
        this.clients = clients.stream()
                .collect(Collectors.toMap(
                        client -> client.provider().toUpperCase(),
                        Function.identity()
                ));
    }

    public PaymentProviderClient getClient(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }

        PaymentProviderClient client = clients.get(provider.toUpperCase());
        if (client == null) {
            throw new IllegalArgumentException("unsupported payment provider");
        }

        return client;
    }
}
