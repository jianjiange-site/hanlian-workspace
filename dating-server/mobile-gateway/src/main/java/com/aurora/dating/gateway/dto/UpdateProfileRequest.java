package com.aurora.dating.gateway.dto;

public record UpdateProfileRequest(
        String nickname,
        Integer age,
        String bio,
        String location,
        String occupation,
        String education,
        Integer height) {
}
