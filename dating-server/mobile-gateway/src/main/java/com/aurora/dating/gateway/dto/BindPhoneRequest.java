package com.aurora.dating.gateway.dto;

public record BindPhoneRequest(
        String phoneE164,
        String smsCode,
        String appName) {
}
