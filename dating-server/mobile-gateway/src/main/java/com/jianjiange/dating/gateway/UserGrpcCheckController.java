package com.jianjiange.dating.gateway;

import com.dating.hanlian.proto.user.v1.PingRequest;
import com.dating.hanlian.proto.user.v1.PingResponse;
import com.dating.hanlian.proto.user.v1.UserServiceGrpc;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserGrpcCheckController {

    private final UserServiceGrpc.UserServiceBlockingStub userServiceBlockingStub;

    public UserGrpcCheckController(UserServiceGrpc.UserServiceBlockingStub userServiceBlockingStub) {
        this.userServiceBlockingStub = userServiceBlockingStub;
    }

    /**
     * Verifies the minimal mobile-gateway to user-service gRPC call chain.
     *
     * @return a small response containing the user-service Ping RPC result
     */
    @GetMapping("/internal/check/user-grpc")
    public Map<String, String> checkUserGrpc() {
        // 1. Build a minimal request using the Java class generated from user.proto.
        PingRequest request = PingRequest.newBuilder()
                .setMessage("hello from mobile-gateway")
                .build();

        // 2. Call user-service through the generated blocking stub.
        PingResponse response = userServiceBlockingStub.ping(request);

        // 3. Return the gRPC result through an internal HTTP check endpoint for easy local testing.
        return Map.of(
                "gateway", "ok",
                "userGrpc", "ok",
                "message", response.getMessage()
        );
    }
}
