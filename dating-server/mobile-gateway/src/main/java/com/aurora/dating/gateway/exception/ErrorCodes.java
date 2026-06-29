package com.aurora.dating.gateway.exception;

public final class ErrorCodes {

    public static final int BAD_REQUEST = 10400;
    public static final int USER_BANNED = 10510;
    public static final int UPSTREAM_UNAVAILABLE = 10901;
    public static final int INTERNAL_ERROR = 10999;
    public static final int TOKEN_INVALID = 10501;

    private ErrorCodes() {
    }
}
