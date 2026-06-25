package com.aurora.dating.gateway;

import com.dating.hanlian.proto.im.v1.ImServiceGrpc;
import com.dating.hanlian.proto.im.v1.PingRequest;
import com.dating.hanlian.proto.im.v1.PingResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ImGrpcCheckController {

    private final ImServiceGrpc.ImServiceBlockingStub imServiceBlockingStub;

    public ImGrpcCheckController(ImServiceGrpc.ImServiceBlockingStub imServiceBlockingStub) {
        this.imServiceBlockingStub = imServiceBlockingStub;
    }

    @GetMapping("/internal/check/im-grpc")
    public Map<String, String> checkImGrpc() {
        PingRequest request = PingRequest.newBuilder()
                .setMessage("hello from mobile-gateway")
                .build();

        PingResponse response = imServiceBlockingStub.ping(request);

        return Map.of(
                "gateway", "ok",
                "imGrpc", "ok",
                "message", response.getMessage()
        );
    }
}
