package com.aurora.dating.gateway.vo;

public record Result<T>(
        int code,
        String message,
        T data) {

    public static <T> Result<T> success(T data) {
        return new Result<>(0, "success", data);
    }

    public static <T> Result<T> failure(int code, String message) {
        return new Result<>(code, message, null);
    }
}
