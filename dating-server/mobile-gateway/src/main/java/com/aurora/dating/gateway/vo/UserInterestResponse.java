package com.aurora.dating.gateway.vo;

import com.dating.hanlian.proto.user.v1.UserInterest;

public record UserInterestResponse(
        String interestCode,
        String displayName,
        Integer type,
        String picKey,
        Integer sortOrder) {

    public static UserInterestResponse from(UserInterest interest) {
        return new UserInterestResponse(
                interest.getInterestCode(),
                interest.getDisplayName(),
                interest.getTypeValue(),
                interest.getPicKey(),
                interest.getSortOrder());
    }
}
