package com.jianjiange.dating.gateway.client;

import com.dating.hanlian.proto.match.v1.MatchServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MatchGrpcClientConfig {

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel matchServiceManagedChannel(
            @Value("${match-service.grpc.host:127.0.0.1}") String host,
            @Value("${match-service.grpc.port:19083}") int port) {
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
    }

    @Bean
    public MatchServiceGrpc.MatchServiceBlockingStub matchServiceBlockingStub(
            @Qualifier("matchServiceManagedChannel") ManagedChannel matchServiceManagedChannel) {
        return MatchServiceGrpc.newBlockingStub(matchServiceManagedChannel);
    }
}
