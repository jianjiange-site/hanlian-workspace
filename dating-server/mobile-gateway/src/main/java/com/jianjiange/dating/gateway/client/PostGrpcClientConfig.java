package com.jianjiange.dating.gateway.client;

import com.dating.hanlian.proto.post.v1.PostServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PostGrpcClientConfig {

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel postServiceManagedChannel(
            @Value("${post-service.grpc.host:127.0.0.1}") String host,
            @Value("${post-service.grpc.port:19084}") int port) {
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
    }

    @Bean
    public PostServiceGrpc.PostServiceBlockingStub postServiceBlockingStub(
            @Qualifier("postServiceManagedChannel") ManagedChannel postServiceManagedChannel) {
        return PostServiceGrpc.newBlockingStub(postServiceManagedChannel);
    }
}
