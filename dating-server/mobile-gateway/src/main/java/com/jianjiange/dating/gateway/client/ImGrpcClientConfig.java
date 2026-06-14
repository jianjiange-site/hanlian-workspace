package com.jianjiange.dating.gateway.client;

import com.dating.hanlian.proto.im.v1.ImServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ImGrpcClientConfig {

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel imServiceManagedChannel(
            @Value("${im-service.grpc.host:127.0.0.1}") String host,
            @Value("${im-service.grpc.port:19082}") int port) {
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
    }

    @Bean
    public ImServiceGrpc.ImServiceBlockingStub imServiceBlockingStub(
            @Qualifier("imServiceManagedChannel") ManagedChannel imServiceManagedChannel) {
        return ImServiceGrpc.newBlockingStub(imServiceManagedChannel);
    }
}
