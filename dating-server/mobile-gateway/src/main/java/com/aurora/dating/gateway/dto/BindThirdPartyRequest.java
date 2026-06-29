package com.aurora.dating.gateway.dto;

public record BindThirdPartyRequest(
        Integer platform,
        String thirdPartyUserId,
        String thirdPartyToken,
        String appName,
        String email) {
}
