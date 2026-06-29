package com.aurora.dating.user.grpc;

import io.grpc.Context;

public final class UserContext {

    static final Context.Key<Long> CALLER_USER_ID = Context.key("callerUserId");
    static final Context.Key<String> DEVICE_ID = Context.key("deviceId");
    static final Context.Key<String> TRACE_ID = Context.key("traceId");

    private UserContext() {
    }

    public static Long callerUserId() {
        return CALLER_USER_ID.get();
    }

    public static String deviceId() {
        return DEVICE_ID.get();
    }

    public static String traceId() {
        return TRACE_ID.get();
    }
}
