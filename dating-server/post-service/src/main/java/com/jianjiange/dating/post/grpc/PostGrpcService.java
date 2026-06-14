package com.jianjiange.dating.post.grpc;

import com.dating.hanlian.proto.post.v1.PingRequest;
import com.dating.hanlian.proto.post.v1.PingResponse;
import com.dating.hanlian.proto.post.v1.PostServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

@Component
public class PostGrpcService extends PostServiceGrpc.PostServiceImplBase {

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        PingResponse response = PingResponse.newBuilder()
                .setMessage("post-service pong: " + request.getMessage())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
