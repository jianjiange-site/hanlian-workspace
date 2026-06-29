package com.aurora.dating.user.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UserContextServerInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> USER_ID_HEADER =
            Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> DEVICE_ID_HEADER =
            Metadata.Key.of("x-device-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> TRACE_ID_HEADER =
            Metadata.Key.of("x-trace-id", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        Long callerUserId = parseLong(headers.get(USER_ID_HEADER));
        String deviceId = trimToNull(headers.get(DEVICE_ID_HEADER));
        String inputTraceId = trimToNull(headers.get(TRACE_ID_HEADER));
        String traceId = inputTraceId == null ? UUID.randomUUID().toString() : inputTraceId;
        Long contextCallerUserId = callerUserId;
        String contextDeviceId = deviceId;
        String contextTraceId = traceId;

        Context context = Context.current()
                .withValue(UserContext.CALLER_USER_ID, contextCallerUserId)
                .withValue(UserContext.DEVICE_ID, contextDeviceId)
                .withValue(UserContext.TRACE_ID, contextTraceId);

        return Contexts.interceptCall(context, call, headers, (serverCall, metadata) -> {
            putMdc(contextCallerUserId, contextDeviceId, contextTraceId);
            try {
                ServerCall.Listener<ReqT> listener = next.startCall(serverCall, metadata);
                return new MdcServerCallListener<>(listener, contextCallerUserId, contextDeviceId, contextTraceId);
            } finally {
                MDC.clear();
            }
        });
    }

    private Long parseLong(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static void putMdc(Long callerUserId, String deviceId, String traceId) {
        if (callerUserId != null) {
            MDC.put("userId", String.valueOf(callerUserId));
        }
        if (deviceId != null) {
            MDC.put("deviceId", deviceId);
        }
        if (traceId != null) {
            MDC.put("traceId", traceId);
        }
    }

    private static final class MdcServerCallListener<ReqT> extends ServerCall.Listener<ReqT> {

        private final ServerCall.Listener<ReqT> delegate;
        private final Long callerUserId;
        private final String deviceId;
        private final String traceId;

        private MdcServerCallListener(
                ServerCall.Listener<ReqT> delegate,
                Long callerUserId,
                String deviceId,
                String traceId) {
            this.delegate = delegate;
            this.callerUserId = callerUserId;
            this.deviceId = deviceId;
            this.traceId = traceId;
        }

        @Override
        public void onMessage(ReqT message) {
            runWithMdc(() -> delegate.onMessage(message));
        }

        @Override
        public void onHalfClose() {
            runWithMdc(delegate::onHalfClose);
        }

        @Override
        public void onCancel() {
            runWithMdc(delegate::onCancel);
        }

        @Override
        public void onComplete() {
            runWithMdc(delegate::onComplete);
        }

        @Override
        public void onReady() {
            runWithMdc(delegate::onReady);
        }

        private void runWithMdc(Runnable runnable) {
            putMdc(callerUserId, deviceId, traceId);
            try {
                runnable.run();
            } finally {
                MDC.clear();
            }
        }
    }
}
