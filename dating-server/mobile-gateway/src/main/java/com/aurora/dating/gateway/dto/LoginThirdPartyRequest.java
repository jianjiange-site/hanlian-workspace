package com.aurora.dating.gateway.dto;

public record LoginThirdPartyRequest(
        Integer platform,
        String thirdPartyUserId,
        String thirdPartyToken,
        String email,
        String appName) {
}
