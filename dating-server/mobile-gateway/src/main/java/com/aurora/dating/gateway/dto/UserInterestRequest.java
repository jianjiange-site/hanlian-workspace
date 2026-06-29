package com.aurora.dating.gateway.dto;

public record UserInterestRequest(
        String interestCode,
        String displayName,
        Integer type,
        String picKey,
        Integer sortOrder) {
}
