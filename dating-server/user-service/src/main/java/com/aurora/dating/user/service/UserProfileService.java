package com.aurora.dating.user.service;

import com.aurora.dating.user.entity.UserInfoEntity;

import java.util.List;

public interface UserProfileService {

    UserInfoEntity getProfile(Long userId);

    List<UserInfoEntity> batchGetProfile(List<Long> userIds);

    UserInfoEntity updateProfile(
            Long userId,
            String nickname,
            Integer age,
            String bio,
            String location,
            String occupation,
            String education,
            Integer height);

    UserInfoEntity upsertOnboarding(
            Long userId,
            String nickname,
            Integer gender,
            Integer age,
            String bio,
            String location,
            String occupation,
            String education,
            Integer height,
            String birthday);

    AvatarUploadSignature presignAvatarUpload(
            Long userId,
            String fileExt,
            String contentType,
            Long contentLength);

    UserInfoEntity confirmAvatarUpload(Long userId, String objectKey);

    List<UserInterestItem> getUserInterests(Long userId);

    List<UserInterestItem> replaceUserInterests(Long userId, List<UserInterestItem> interests);

    UserInfoEntity updateRegulationStatus(Long userId, Integer regulationStatus, String reason);

    List<RegulationLogItem> listRegulationLogs(Long userId, Integer limit);

    record AvatarUploadSignature(
            String uploadUrl,
            String objectKey,
            Long expireSeconds) {
    }

    record UserInterestItem(
            String interestCode,
            String displayName,
            Integer type,
            String picKey,
            Integer sortOrder) {
    }

    record RegulationLogItem(
            Long id,
            Long userId,
            Integer beforeStatus,
            Integer afterStatus,
            String reason,
            Integer operatorType,
            String operatorId,
            Long createdAtMs) {
    }
}
