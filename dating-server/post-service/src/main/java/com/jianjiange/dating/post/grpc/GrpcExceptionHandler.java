package com.jianjiange.dating.post.grpc;

import com.jianjiange.dating.post.exception.BusinessException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

public class GrpcExceptionHandler {

    private GrpcExceptionHandler() {
    }

    public static void handle(Exception e, StreamObserver<?> responseObserver) {
        Status status = toStatus(e);
        responseObserver.onError(status
                .withDescription(e.getMessage())
                .asRuntimeException());
    }

    private static Status toStatus(Exception e) {
        if (e instanceof BusinessException businessException) {
            return switch (businessException.getStatus()) {
                case NOT_FOUND -> Status.NOT_FOUND;
                case FORBIDDEN -> Status.PERMISSION_DENIED;
                case BAD_REQUEST -> Status.INVALID_ARGUMENT;
                default -> Status.UNKNOWN;
            };
        }

        if (e instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT;
        }

        return Status.INTERNAL;
    }
}
