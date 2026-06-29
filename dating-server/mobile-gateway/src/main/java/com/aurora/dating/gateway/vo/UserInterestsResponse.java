package com.aurora.dating.gateway.vo;

import java.util.List;

public record UserInterestsResponse(
        List<UserInterestResponse> interests) {
}
