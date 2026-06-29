package com.aurora.dating.gateway.client;

import com.aurora.dating.gateway.exception.BizException;
import com.aurora.dating.gateway.exception.ErrorCodes;
import com.dating.hanlian.proto.user.v1.BatchGetProfileRequest;
import com.dating.hanlian.proto.user.v1.ConfirmAvatarUploadRequest;
import com.dating.hanlian.proto.user.v1.GetProfileRequest;
import com.dating.hanlian.proto.user.v1.GetUserInterestsRequest;
import com.dating.hanlian.proto.user.v1.PresignAvatarUploadRequest;
import com.dating.hanlian.proto.user.v1.PresignAvatarUploadResponse;
import com.dating.hanlian.proto.user.v1.ReplaceUserInterestsRequest;
import com.dating.hanlian.proto.user.v1.UpsertOnboardingRequest;
import com.dating.hanlian.proto.user.v1.UserInterest;
import com.dating.hanlian.proto.user.v1.UserProfile;
import com.dating.hanlian.proto.user.v1.UserProfileServiceGrpc;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserProfileClient {

    private final UserProfileServiceGrpc.UserProfileServiceBlockingStub userProfileServiceBlockingStub;

    public UserProfileClient(UserProfileServiceGrpc.UserProfileServiceBlockingStub userProfileServiceBlockingStub) {
        this.userProfileServiceBlockingStub = userProfileServiceBlockingStub;
    }

    public UserProfile getProfile(long userId) {
        GetProfileRequest request = GetProfileRequest.newBuilder()
                .setUserId(userId)
                .build();
        try {
            return userProfileServiceBlockingStub.getProfile(request).getProfile();
        } catch (StatusRuntimeException e) {
            throw toBizException(e);
        }
    }

    public List<UserProfile> batchGetProfile(List<Long> userIds) {
        BatchGetProfileRequest request = BatchGetProfileRequest.newBuilder()
                .addAllUserIds(userIds)
                .build();
        try {
            return userProfileServiceBlockingStub.batchGetProfile(request).getProfilesList();
        } catch (StatusRuntimeException e) {
            throw toBizException(e);
        }
    }

    public PresignAvatarUploadResponse presignAvatarUpload(
            long userId,
            String fileExt,
            String contentType,
            Long contentLength) {
        PresignAvatarUploadRequest request = PresignAvatarUploadRequest.newBuilder()
                .setUserId(userId)
                .setFileExt(valueOrEmpty(fileExt))
                .setContentType(valueOrEmpty(contentType))
                .setContentLength(valueOrZero(contentLength))
                .build();
        try {
            return userProfileServiceBlockingStub.presignAvatarUpload(request);
        } catch (StatusRuntimeException e) {
            throw toBizException(e);
        }
    }

    public UserProfile confirmAvatarUpload(long userId, String objectKey) {
        ConfirmAvatarUploadRequest request = ConfirmAvatarUploadRequest.newBuilder()
                .setUserId(userId)
                .setObjectKey(valueOrEmpty(objectKey))
                .build();
        try {
            return userProfileServiceBlockingStub.confirmAvatarUpload(request).getProfile();
        } catch (StatusRuntimeException e) {
            throw toBizException(e);
        }
    }

    public UserProfile upsertOnboarding(
            long userId,
            String nickname,
            Integer gender,
            Integer age,
            String birthday,
            String bio,
            String location,
            String occupation,
            String education,
            Integer height) {
        UpsertOnboardingRequest request = UpsertOnboardingRequest.newBuilder()
                .setUserId(userId)
                .setNickname(valueOrEmpty(nickname))
                .setGender(valueOrZero(gender))
                .setAge(valueOrZero(age))
                .setBirthday(valueOrEmpty(birthday))
                .setBio(valueOrEmpty(bio))
                .setLocation(valueOrEmpty(location))
                .setOccupation(valueOrEmpty(occupation))
                .setEducation(valueOrEmpty(education))
                .setHeight(valueOrZero(height))
                .build();
        try {
            return userProfileServiceBlockingStub.upsertOnboarding(request).getProfile();
        } catch (StatusRuntimeException e) {
            throw toBizException(e);
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private BizException toBizException(StatusRuntimeException e) {
        String message = e.getStatus().getDescription();
        if (message == null || message.isBlank()) {
            message = "upstream user-service unavailable";
        }
        return new BizException(ErrorCodes.UPSTREAM_UNAVAILABLE, message);
    }

    public UserProfile updateProfile(
            long userId,
            String nickname,
            Integer age,
            String bio,
            String location,
            String occupation,
            String education,
            Integer height) {
        com.dating.hanlian.proto.user.v1.UpdateProfileRequest request =
                com.dating.hanlian.proto.user.v1.UpdateProfileRequest.newBuilder()
                        .setUserId(userId)
                        .setNickname(valueOrEmpty(nickname))
                        .setAge(valueOrZero(age))
                        .setBio(valueOrEmpty(bio))
                        .setLocation(valueOrEmpty(location))
                        .setOccupation(valueOrEmpty(occupation))
                        .setEducation(valueOrEmpty(education))
                        .setHeight(valueOrZero(height))
                        .build();

        try {
            return userProfileServiceBlockingStub.updateProfile(request).getProfile();
        } catch (StatusRuntimeException e) {
            throw toBizException(e);
        }
    }

    public List<UserInterest> getUserInterests(long userId) {
        GetUserInterestsRequest request = GetUserInterestsRequest.newBuilder()
                .setUserId(userId)
                .build();
        try {
            return userProfileServiceBlockingStub.getUserInterests(request).getInterestsList();
        } catch (StatusRuntimeException e) {
            throw toBizException(e);
        }
    }

    public List<UserInterest> replaceUserInterests(
            long userId,
            List<com.aurora.dating.gateway.dto.UserInterestRequest> interests) {
        ReplaceUserInterestsRequest.Builder builder = ReplaceUserInterestsRequest.newBuilder()
                .setUserId(userId);
        if (interests != null) {
            for (com.aurora.dating.gateway.dto.UserInterestRequest interest : interests) {
                builder.addInterests(UserInterest.newBuilder()
                        .setInterestCode(valueOrEmpty(interest.interestCode()))
                        .setDisplayName(valueOrEmpty(interest.displayName()))
                        .setTypeValue(valueOrZero(interest.type()))
                        .setPicKey(valueOrEmpty(interest.picKey()))
                        .setSortOrder(valueOrZero(interest.sortOrder()))
                        .build());
            }
        }

        try {
            return userProfileServiceBlockingStub.replaceUserInterests(builder.build()).getInterestsList();
        } catch (StatusRuntimeException e) {
            throw toBizException(e);
        }
    }
}
