package com.aurora.dating.post.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class PostGrpcServerLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(PostGrpcServerLifecycle.class);

    private final PostGrpcService postGrpcService;
    private final int port;
    private Server server;
    private boolean running;

    public PostGrpcServerLifecycle(
            PostGrpcService postGrpcService,
            @Value("${grpc.server.port:19084}") int port) {
        this.postGrpcService = postGrpcService;
        this.port = port;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }

        try {
            server = ServerBuilder.forPort(port)
                    .addService(postGrpcService)
                    .build()
                    .start();

            running = true;
            log.info("post-service gRPC server started on port {}", port);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start post-service gRPC server on port " + port, e);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.shutdown();
        }
        running = false;
        log.info("post-service gRPC server stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
