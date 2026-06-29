package com.aurora.dating.gateway.security;

public final class JwtUserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private JwtUserContext() {
    }

    public static void setUserId(long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
    }
}
