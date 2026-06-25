package com.aurora.dating.payment.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class PaymentGrpcServerLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(PaymentGrpcServerLifecycle.class);

    private final PaymentGrpcService paymentGrpcService;
    private final int port;
    private Server server;
    private boolean running;

    public PaymentGrpcServerLifecycle(
            PaymentGrpcService paymentGrpcService,
            @Value("${grpc.server.port:19085}") int port) {
        this.paymentGrpcService = paymentGrpcService;
        this.port = port;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }

        try {
            server = ServerBuilder.forPort(port)
                    .addService(paymentGrpcService)
                    .build()
                    .start();

            running = true;
            log.info("payment-service gRPC server started on port {}", port);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start payment-service gRPC server on port " + port, e);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.shutdown();
        }
        running = false;
        log.info("payment-service gRPC server stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
