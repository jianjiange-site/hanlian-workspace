package com.aurora.dating.gateway.dto;

import java.util.List;

public record ReplaceUserInterestsRequest(
        List<UserInterestRequest> interests) {
}
