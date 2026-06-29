package com.aurora.dating.gateway.client;

import com.aurora.dating.gateway.security.JwtUserContext;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Component
public class UserGrpcClientMetadataInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> USER_ID_HEADER =
            Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> DEVICE_ID_HEADER =
            Metadata.Key.of("x-device-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> TRACE_ID_HEADER =
            Metadata.Key.of("x-trace-id", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                Long userId = JwtUserContext.getUserId();
                if (userId != null) {
                    headers.put(USER_ID_HEADER, String.valueOf(userId));
                }

                String deviceId = currentRequestHeader("x-device-id");
                if (deviceId != null) {
                    headers.put(DEVICE_ID_HEADER, deviceId);
                }

                String traceId = currentRequestHeader("x-trace-id");
                if (traceId == null) {
                    traceId = UUID.randomUUID().toString();
                }
                headers.put(TRACE_ID_HEADER, traceId);

                super.start(responseListener, headers);
            }
        };
    }

    private String currentRequestHeader(String name) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return null;
        }

        HttpServletRequest request = servletRequestAttributes.getRequest();
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
