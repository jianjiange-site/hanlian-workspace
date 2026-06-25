package com.aurora.dating.gateway;

import com.dating.hanlian.proto.match.v1.MatchServiceGrpc;
import com.dating.hanlian.proto.match.v1.PingRequest;
import com.dating.hanlian.proto.match.v1.PingResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MatchGrpcCheckController {

    private final MatchServiceGrpc.MatchServiceBlockingStub matchServiceBlockingStub;

    public MatchGrpcCheckController(MatchServiceGrpc.MatchServiceBlockingStub matchServiceBlockingStub) {
        this.matchServiceBlockingStub = matchServiceBlockingStub;
    }

    @GetMapping("/internal/check/match-grpc")
    public Map<String, String> checkMatchGrpc() {
        PingRequest request = PingRequest.newBuilder()
                .setMessage("hello from mobile-gateway")
                .build();

        PingResponse response = matchServiceBlockingStub.ping(request);

        return Map.of(
                "gateway", "ok",
                "matchGrpc", "ok",
                "message", response.getMessage()
        );
    }
}
