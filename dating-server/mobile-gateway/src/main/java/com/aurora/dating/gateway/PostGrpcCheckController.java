package com.aurora.dating.gateway;

import com.dating.hanlian.proto.post.v1.PingRequest;
import com.dating.hanlian.proto.post.v1.PingResponse;
import com.dating.hanlian.proto.post.v1.PostServiceGrpc;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PostGrpcCheckController {

    private final PostServiceGrpc.PostServiceBlockingStub postServiceBlockingStub;

    public PostGrpcCheckController(PostServiceGrpc.PostServiceBlockingStub postServiceBlockingStub) {
        this.postServiceBlockingStub = postServiceBlockingStub;
    }

    @GetMapping("/internal/check/post-grpc")
    public Map<String, String> checkPostGrpc() {
        PingRequest request = PingRequest.newBuilder()
                .setMessage("hello from mobile-gateway")
                .build();

        PingResponse response = postServiceBlockingStub.ping(request);

        return Map.of(
                "gateway", "ok",
                "postGrpc", "ok",
                "message", response.getMessage()
        );
    }
}
