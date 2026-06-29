package com.aurora.dating.gateway.vo;

public record PresignAvatarUploadResponse(
        String uploadUrl,
        String objectKey,
        Long expireSeconds) {
}
