package com.aurora.dating.gateway.dto;

public record PresignAvatarUploadRequest(
        String fileExt,
        String contentType,
        Long contentLength) {
}
