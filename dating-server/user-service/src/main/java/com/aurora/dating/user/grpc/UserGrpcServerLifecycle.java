package com.aurora.dating.user.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class UserGrpcServerLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(UserGrpcServerLifecycle.class);
    private final UserIdentityGrpcService userIdentityGrpcService;

    private final UserGrpcService userGrpcService;
    private final int port;
    private Server server;
    private boolean running;

    public UserGrpcServerLifecycle(
            UserGrpcService userGrpcService,
            UserIdentityGrpcService userIdentityGrpcService,
            @Value("${grpc.server.port:19081}") int grpcPort) {
        this.userGrpcService = userGrpcService;
        this.userIdentityGrpcService = userIdentityGrpcService;
        this.port = grpcPort;
    }

    /**
     * Starts a minimal in-process managed gRPC server for user-service.
     *
     * <p>The service keeps REST on port 18081 and exposes gRPC on port 19081 so the two protocols do
     * not fight for the same port.
     */
    @Override
    public void start() {
        if (running) {
            return;
        }

        try {
            // 1. Bind the generated UserService contract implementation to a dedicated gRPC port.
            server = ServerBuilder.forPort(port)
                    .addService(userGrpcService)
                    .addService(userIdentityGrpcService)
                    .build()
                    .start();

            // 2. Mark this lifecycle bean as running so Spring can stop it cleanly on shutdown.
            running = true;
            log.info("user-service gRPC server started on port {}", port);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start user-service gRPC server on port " + port, e);
        }
    }

    /**
     * Stops the gRPC server when Spring Boot shuts down.
     */
    @Override
    public void stop() {
        if (server != null) {
            server.shutdown();
        }
        running = false;
        log.info("user-service gRPC server stopped");
    }

    /**
     * Returns whether the gRPC server is currently running.
     *
     * @return true after the server has started and before it has been stopped
     */
    @Override
    public boolean isRunning() {
        return running;
    }
}
