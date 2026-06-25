package com.aurora.dating.match.grpc;

import com.dating.hanlian.proto.match.v1.MatchServiceGrpc;
import com.dating.hanlian.proto.match.v1.PingRequest;
import com.dating.hanlian.proto.match.v1.PingResponse;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

@Component
public class MatchGrpcService extends MatchServiceGrpc.MatchServiceImplBase {

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        PingResponse response = PingResponse.newBuilder()
                .setMessage("match-service pong: " + request.getMessage())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
