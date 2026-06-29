package com.aurora.dating.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.aurora.dating.gateway.mapper")
public class MobileGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MobileGatewayApplication.class, args);
    }
}
