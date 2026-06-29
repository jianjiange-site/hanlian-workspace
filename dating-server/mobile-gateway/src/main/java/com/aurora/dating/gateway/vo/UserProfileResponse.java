package com.aurora.dating.gateway.vo;

import com.dating.hanlian.proto.user.v1.Avatar;
import com.dating.hanlian.proto.user.v1.UserProfile;

import java.util.List;

public record UserProfileResponse(
        Long userId,
        String appName,
        Boolean pending,
        String nickname,
        Integer gender,
        Integer age,
        String birthday,
        String bio,
        String location,
        String occupation,
        String education,
        Integer height,
        Integer regulationStatus,
        Integer profileCompletion,
        List<String> missingFields,
        AvatarResponse avatar,
        List<UserInterestResponse> interests) {

    public static UserProfileResponse from(UserProfile profile) {
        return new UserProfileResponse(
                profile.getUserId(),
                profile.getAppName(),
                profile.getPending(),
                profile.getNickname(),
                profile.getGender(),
                profile.getAge(),
                profile.getBirthday(),
                profile.getBio(),
                profile.getLocation(),
                profile.getOccupation(),
                profile.getEducation(),
                profile.getHeight(),
                profile.getRegulationStatus(),
                profile.getProfileCompletion(),
                profile.getMissingFieldsList(),
                toAvatarResponse(profile),
                profile.getInterestsList().stream()
                        .map(UserInterestResponse::from)
                        .toList());
    }

    private static AvatarResponse toAvatarResponse(UserProfile profile) {
        if (!profile.hasAvatar()) {
            return null;
        }

        Avatar avatar = profile.getAvatar();
        if (avatar.getOriginalKey().isBlank()
                && avatar.getOriginalUrl().isBlank()
                && avatar.getMinKey().isBlank()
                && avatar.getMinUrl().isBlank()
                && avatar.getMidKey().isBlank()
                && avatar.getMidUrl().isBlank()
                && avatar.getStatus().isBlank()) {
            return null;
        }

        return new AvatarResponse(
                avatar.getOriginalKey(),
                avatar.getOriginalUrl(),
                avatar.getMinKey(),
                avatar.getMinUrl(),
                avatar.getMidKey(),
                avatar.getMidUrl(),
                avatar.getStatus());
    }

    public record AvatarResponse(
            String originalKey,
            String originalUrl,
            String minKey,
            String minUrl,
            String midKey,
            String midUrl,
            String status) {
    }
}
