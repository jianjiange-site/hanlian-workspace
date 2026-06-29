package com.aurora.dating.gateway.dto;

import java.util.List;

public record BatchGetProfilesRequest(
        List<Long> userIds) {
}
