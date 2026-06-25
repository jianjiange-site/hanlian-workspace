package com.aurora.dating.gateway.client;

import com.dating.hanlian.proto.payment.v1.PaymentServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentGrpcClientConfig {

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel paymentServiceManagedChannel(
            @Value("${payment-service.grpc.host:127.0.0.1}") String host,
            @Value("${payment-service.grpc.port:19085}") int port) {
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
    }

    @Bean
    public PaymentServiceGrpc.PaymentServiceBlockingStub paymentServiceBlockingStub(
            @Qualifier("paymentServiceManagedChannel") ManagedChannel paymentServiceManagedChannel) {
        return PaymentServiceGrpc.newBlockingStub(paymentServiceManagedChannel);
    }
}
