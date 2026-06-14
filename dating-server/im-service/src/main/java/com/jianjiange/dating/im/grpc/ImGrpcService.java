package com.jianjiange.dating.im.grpc;

import com.dating.hanlian.proto.im.v1.ImServiceGrpc;
import com.dating.hanlian.proto.im.v1.PingRequest;
import com.dating.hanlian.proto.im.v1.PingResponse;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

@Component
public class ImGrpcService extends ImServiceGrpc.ImServiceImplBase {

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        PingResponse response = PingResponse.newBuilder()
                .setMessage("im-service pong: " + request.getMessage())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
