package com.aurora.dating.user.grpc;

import com.dating.hanlian.proto.user.v1.PingRequest;
import com.dating.hanlian.proto.user.v1.PingResponse;
import com.dating.hanlian.proto.user.v1.UserServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

@Component
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    /**
     * Handles the minimal Ping RPC used to verify that user-service can implement the generated
     * user-proto gRPC contract.
     *
     * @param request ping request generated from proto/user/src/main/proto/user.proto
     * @param responseObserver gRPC response stream used to return exactly one PingResponse
     */
    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        // 1. Build a tiny response so callers can confirm they reached user-service over gRPC.
        PingResponse response = PingResponse.newBuilder()
                .setMessage("user-service pong: " + request.getMessage())
                .build();

        // 2. Return the response and close the unary RPC stream.
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
