package com.aurora.dating.im.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class ImGrpcServerLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ImGrpcServerLifecycle.class);

    private final ImGrpcService imGrpcService;
    private final int port;
    private Server server;
    private boolean running;

    public ImGrpcServerLifecycle(
            ImGrpcService imGrpcService,
            @Value("${grpc.server.port:19082}") int port) {
        this.imGrpcService = imGrpcService;
        this.port = port;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }

        try {
            server = ServerBuilder.forPort(port)
                    .addService(imGrpcService)
                    .build()
                    .start();

            running = true;
            log.info("im-service gRPC server started on port {}", port);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start im-service gRPC server on port " + port, e);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.shutdown();
        }
        running = false;
        log.info("im-service gRPC server stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
