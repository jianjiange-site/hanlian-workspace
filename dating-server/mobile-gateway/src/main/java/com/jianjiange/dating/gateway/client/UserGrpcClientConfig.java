package com.jianjiange.dating.gateway.client;

import com.dating.hanlian.proto.user.v1.UserServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserGrpcClientConfig {

    /**
     * Creates one reusable gRPC channel from mobile-gateway to user-service.
     *
     * @param host user-service gRPC host; local development defaults to 127.0.0.1
     * @param port user-service gRPC port; local development defaults to 19081
     * @return a plaintext local-development channel that Spring closes on shutdown
     */
    @Bean(destroyMethod = "shutdown")
    public ManagedChannel userServiceManagedChannel(
            @Value("${user-service.grpc.host:127.0.0.1}") String host,
            @Value("${user-service.grpc.port:19081}") int port) {
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
    }

    /**
     * Creates a blocking stub for the user-service Ping RPC.
     *
     * @param userServiceManagedChannel shared gRPC channel to user-service
     * @return generated blocking stub from user-proto
     */
    @Bean
    public UserServiceGrpc.UserServiceBlockingStub userServiceBlockingStub(
            @Qualifier("userServiceManagedChannel") ManagedChannel userServiceManagedChannel) {
        return UserServiceGrpc.newBlockingStub(userServiceManagedChannel);
    }
}
