package com.aurora.dating.gateway.vo;

public record LoginResponse(
        Long userId,
        Boolean pending,
        Boolean created,
        Boolean banned,
        String banReason,
        String banMessage,
        String accessToken,
        String refreshToken,
        Long expiresIn) {
}