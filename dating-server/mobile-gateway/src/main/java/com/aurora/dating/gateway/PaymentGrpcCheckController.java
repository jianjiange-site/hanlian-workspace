package com.aurora.dating.gateway;

import com.dating.hanlian.proto.payment.v1.PaymentServiceGrpc;
import com.dating.hanlian.proto.payment.v1.PingRequest;
import com.dating.hanlian.proto.payment.v1.PingResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentGrpcCheckController {

    private final PaymentServiceGrpc.PaymentServiceBlockingStub paymentServiceBlockingStub;

    public PaymentGrpcCheckController(PaymentServiceGrpc.PaymentServiceBlockingStub paymentServiceBlockingStub) {
        this.paymentServiceBlockingStub = paymentServiceBlockingStub;
    }

    @GetMapping("/internal/check/payment-grpc")
    public Map<String, String> checkPaymentGrpc() {
        PingRequest request = PingRequest.newBuilder()
                .setMessage("hello from mobile-gateway")
                .build();

        PingResponse response = paymentServiceBlockingStub.ping(request);

        return Map.of(
                "gateway", "ok",
                "paymentGrpc", "ok",
                "message", response.getMessage()
        );
    }
}
