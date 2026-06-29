package com.aurora.dating.gateway.security;

public record TokenPair(
        String accessToken,
        String refreshToken,
        long expiresIn,
        long refreshExpiresIn) {
}