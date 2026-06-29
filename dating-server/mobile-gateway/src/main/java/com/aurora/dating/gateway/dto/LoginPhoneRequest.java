package com.aurora.dating.gateway.dto;

public record LoginPhoneRequest(
        String phoneE164,
        String smsCode,
        String appName) {
}
