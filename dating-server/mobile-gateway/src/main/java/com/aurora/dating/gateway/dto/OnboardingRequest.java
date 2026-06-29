package com.aurora.dating.gateway.dto;

public record OnboardingRequest(
        String nickname,
        Integer gender,
        Integer age,
        String birthday,
        String bio,
        String location,
        String occupation,
        String education,
        Integer height) {
}
