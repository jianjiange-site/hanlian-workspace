package com.aurora.dating.payment.grpc;

import com.dating.hanlian.proto.payment.v1.PaymentServiceGrpc;
import com.dating.hanlian.proto.payment.v1.PingRequest;
import com.dating.hanlian.proto.payment.v1.PingResponse;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

@Component
public class PaymentGrpcService extends PaymentServiceGrpc.PaymentServiceImplBase {

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        PingResponse response = PingResponse.newBuilder()
                .setMessage("payment-service pong: " + request.getMessage())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
