package com.aurora.dating.gateway.vo;

import java.util.List;

public record BatchGetProfilesResponse(
        List<UserProfileResponse> profiles) {
}
