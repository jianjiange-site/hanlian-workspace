package com.aurora.dating.user.grpc;

import com.aurora.dating.user.entity.UserInfoEntity;
import com.aurora.dating.user.service.UserProfileService;
import com.aurora.dating.user.storage.StorageProperties;
import com.dating.hanlian.proto.user.v1.Avatar;
import com.dating.hanlian.proto.user.v1.BatchGetProfileRequest;
import com.dating.hanlian.proto.user.v1.BatchGetProfileResponse;
import com.dating.hanlian.proto.user.v1.ConfirmAvatarUploadRequest;
import com.dating.hanlian.proto.user.v1.ConfirmAvatarUploadResponse;
import com.dating.hanlian.proto.user.v1.GetProfileRequest;
import com.dating.hanlian.proto.user.v1.GetProfileResponse;
import com.dating.hanlian.proto.user.v1.GetUserInterestsRequest;
import com.dating.hanlian.proto.user.v1.GetUserInterestsResponse;
import com.dating.hanlian.proto.user.v1.InterestType;
import com.dating.hanlian.proto.user.v1.ListRegulationLogsRequest;
import com.dating.hanlian.proto.user.v1.ListRegulationLogsResponse;
import com.dating.hanlian.proto.user.v1.PresignAvatarUploadRequest;
import com.dating.hanlian.proto.user.v1.PresignAvatarUploadResponse;
import com.dating.hanlian.proto.user.v1.RegulationLog;
import com.dating.hanlian.proto.user.v1.ReplaceUserInterestsRequest;
import com.dating.hanlian.proto.user.v1.ReplaceUserInterestsResponse;
import com.dating.hanlian.proto.user.v1.UpdateProfileRequest;
import com.dating.hanlian.proto.user.v1.UpdateProfileResponse;
import com.dating.hanlian.proto.user.v1.UpdateRegulationStatusRequest;
import com.dating.hanlian.proto.user.v1.UpdateRegulationStatusResponse;
import com.dating.hanlian.proto.user.v1.UpsertOnboardingRequest;
import com.dating.hanlian.proto.user.v1.UpsertOnboardingResponse;
import com.dating.hanlian.proto.user.v1.UserInterest;
import com.dating.hanlian.proto.user.v1.UserProfile;
import com.dating.hanlian.proto.user.v1.UserProfileServiceGrpc;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserProfileGrpcService extends UserProfileServiceGrpc.UserProfileServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(UserProfileGrpcService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String AVATAR_STATUS_DEFAULT = "DEFAULT";

    private final UserProfileService userProfileService;
    private final StorageProperties storageProperties;

    public UserProfileGrpcService(UserProfileService userProfileService, StorageProperties storageProperties) {
        this.userProfileService = userProfileService;
        this.storageProperties = storageProperties;
    }

    @Override
    public void getProfile(GetProfileRequest request, StreamObserver<GetProfileResponse> responseObserver) {
        try {
            UserInfoEntity user = userProfileService.getProfile(request.getUserId());
            GetProfileResponse response = GetProfileResponse.newBuilder()
                    .setProfile(toUserProfile(user))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleException(e, responseObserver);
        }
    }

    @Override
    public void batchGetProfile(
            BatchGetProfileRequest request,
            StreamObserver<BatchGetProfileResponse> responseObserver) {
        try {
            List<UserInfoEntity> users = userProfileService.batchGetProfile(request.getUserIdsList());
            BatchGetProfileResponse.Builder responseBuilder = BatchGetProfileResponse.newBuilder();
            for (UserInfoEntity user : users) {
                responseBuilder.addProfiles(toUserProfile(user));
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleException(e, responseObserver);
        }
    }

    @Override
    public void updateProfile(UpdateProfileRequest request, StreamObserver<UpdateProfileResponse> responseObserver) {
        try {
            UserInfoEntity user = userProfileService.updateProfile(
                    request.getUserId(),
                    request.getNickname(),
                    request.getAge(),
                    request.getBio(),
                    request.getLocation(),
                    request.getOccupation(),
                    request.getEducation(),
                    request.getHeight());

            UpdateProfileResponse response = UpdateProfileResponse.newBuilder()
                    .setProfile(toUserProfile(user))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleException(e, responseObserver);
        }
    }

    @Override
    public void upsertOnboarding(
            UpsertOnboardingRequest request,
            StreamObserver<UpsertOnboardingResponse> responseObserver) {
        try {
            UserInfoEntity user = userProfileService.upsertOnboarding(
                    request.getUserId(),
                    request.getNickname(),
                    request.getGender(),
                    request.getAge(),
                    request.getBio(),
                    request.getLocation(),
                    request.getOccupation(),
                    request.getEducation(),
                    request.getHeight(),
                    request.getBirthday());

            UpsertOnboardingResponse response = UpsertOnboardingResponse.newBuilder()
                    .setProfile(toUserProfile(user))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleException(e, responseObserver);
        }
    }

    @Override
    public void presignAvatarUpload(
            PresignAvatarUploadRequest request,
            StreamObserver<PresignAvatarUploadResponse> responseObserver) {
        try {
            UserProfileService.AvatarUploadSignature signature = userProfileService.presignAvatarUpload(
                    request.getUserId(),
                    request.getFileExt(),
                    request.getContentType(),
                    request.getContentLength());
            PresignAvatarUploadResponse response = PresignAvatarUploadResponse.newBuilder()
                    .setUploadUrl(valueOrEmpty(signature.uploadUrl()))
                    .setObjectKey(valueOrEmpty(signature.objectKey()))
                    .setExpireSeconds(valueOrZero(signature.expireSeconds()))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleException(e, responseObserver);
        }
    }

    @Override
    public void confirmAvatarUpload(
            ConfirmAvatarUploadRequest request,
            StreamObserver<ConfirmAvatarUploadResponse> responseObserver) {
        try {
            UserInfoEntity user = userProfileService.confirmAvatarUpload(
                    request.getUserId(),
                    request.getObjectKey());
            ConfirmAvatarUploadResponse response = ConfirmAvatarUploadResponse.newBuilder()
                    .setProfile(toUserProfile(user))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleException(e, responseObserver);
        }
    }

    @Override
    public void getUserInterests(
            GetUserInterestsRequest request,
            StreamObserver<GetUserInterestsResponse> responseObserver) {
        try {
            List<UserProfileService.UserInterestItem> interests =
                    userProfileService.getUserInterests(request.getUserId());
            GetUserInterestsResponse.Builder responseBuilder = GetUserInterestsResponse.newBuilder();
            for (UserProfileService.UserInterestItem interest : interests) {
                responseBuilder.addInterests(toUserInterest(interest));
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleException(e, responseObserver);
        }
    }

    @Override
    public void replaceUserInterests(
            ReplaceUserInterestsRequest request,
            StreamObserver<ReplaceUserInterestsResponse> responseObserver) {
        try {
            List<UserProfileService.UserInterestItem> input = request.getInterestsList().stream()
                    .map(this::toUserInterestItem)
                    .toList();
            List<UserProfileService.UserInterestItem> interests =
                    userProfileService.replaceUserInterests(request.getUserId(), input);
            ReplaceUserInterestsResponse.Builder responseBuilder = ReplaceUserInterestsResponse.newBuilder();
            for (UserProfileService.UserInterestItem interest : interests) {
                responseBuilder.addInterests(toUserInterest(interest));
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleException(e, responseObserver);
        }
    }

    @Override
    public void updateRegulationStatus(
            UpdateRegulationStatusRequest request,
            StreamObserver<UpdateRegulationStatusResponse> responseObserver) {
        try {
            UserInfoEntity user = userProfileService.updateRegulationStatus(
                    request.getUserId(),
                    request.getRegulationStatus(),
                    request.getReason());
            UpdateRegulationStatusResponse response = UpdateRegulationStatusResponse.newBuilder()
                    .setProfile(toUserProfile(user))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleException(e, responseObserver);
        }
    }

    @Override
    public void listRegulationLogs(
            ListRegulationLogsRequest request,
            StreamObserver<ListRegulationLogsResponse> responseObserver) {
        try {
            List<UserProfileService.RegulationLogItem> logs =
                    userProfileService.listRegulationLogs(request.getUserId(), request.getLimit());
            ListRegulationLogsResponse.Builder responseBuilder = ListRegulationLogsResponse.newBuilder();
            for (UserProfileService.RegulationLogItem logItem : logs) {
                responseBuilder.addLogs(toRegulationLog(logItem));
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleException(e, responseObserver);
        }
    }

    private UserProfile toUserProfile(UserInfoEntity user) {
        UserProfile.Builder builder = UserProfile.newBuilder()
                .setUserId(valueOrZero(user.getUserId()))
                .setAppName(valueOrEmpty(user.getAppName()))
                .setPending(Boolean.TRUE.equals(user.getPending()))
                .setNickname(valueOrEmpty(user.getNickname()))
                .setGender(valueOrZero(user.getGender()))
                .setAge(valueOrZero(user.getAge()))
                .setBirthday(user.getBirthday() == null ? "" : user.getBirthday().toString())
                .setBio(valueOrEmpty(user.getBio()))
                .setLocation(valueOrEmpty(user.getPreferredLocation()))
                .setOccupation(valueOrEmpty(user.getProfession()))
                .setEducation(valueOrEmpty(user.getEducation()))
                .setHeight(valueOrZero(user.getHeight()))
                .setRegulationStatus(valueOrZero(user.getRegulationStatus()));

        Avatar avatar = toAvatarOrDefault(user.getCustomAvatar());
        if (avatar != null) {
            builder.setAvatar(avatar);
        }

        ProfileCompletion profileCompletion = calculateProfileCompletion(user, avatar);
        builder.setProfileCompletion(profileCompletion.score())
                .addAllMissingFields(profileCompletion.missingFields());

        List<UserProfileService.UserInterestItem> interests =
                userProfileService.getUserInterests(user.getUserId());
        for (UserProfileService.UserInterestItem interest : interests) {
            builder.addInterests(toUserInterest(interest));
        }

        return builder.build();
    }

    private RegulationLog toRegulationLog(UserProfileService.RegulationLogItem logItem) {
        return RegulationLog.newBuilder()
                .setId(valueOrZero(logItem.id()))
                .setUserId(valueOrZero(logItem.userId()))
                .setBeforeStatus(valueOrZero(logItem.beforeStatus()))
                .setAfterStatus(valueOrZero(logItem.afterStatus()))
                .setReason(valueOrEmpty(logItem.reason()))
                .setOperatorType(valueOrZero(logItem.operatorType()))
                .setOperatorId(valueOrEmpty(logItem.operatorId()))
                .setCreatedAtMs(valueOrZero(logItem.createdAtMs()))
                .build();
    }

    private UserInterest toUserInterest(UserProfileService.UserInterestItem interest) {
        return UserInterest.newBuilder()
                .setInterestCode(valueOrEmpty(interest.interestCode()))
                .setDisplayName(valueOrEmpty(interest.displayName()))
                .setType(toInterestType(interest.type()))
                .setPicKey(valueOrEmpty(interest.picKey()))
                .setSortOrder(valueOrZero(interest.sortOrder()))
                .build();
    }

    private UserProfileService.UserInterestItem toUserInterestItem(UserInterest interest) {
        return new UserProfileService.UserInterestItem(
                interest.getInterestCode(),
                interest.getDisplayName(),
                interest.getTypeValue(),
                interest.getPicKey(),
                interest.getSortOrder());
    }

    private InterestType toInterestType(Integer type) {
        if (type == null) {
            return InterestType.INTEREST_TYPE_UNSPECIFIED;
        }
        return InterestType.forNumber(type) == null
                ? InterestType.INTEREST_TYPE_UNSPECIFIED
                : InterestType.forNumber(type);
    }

    private Avatar toAvatar(String customAvatar) {
        if (customAvatar == null || customAvatar.isBlank()) {
            return null;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(customAvatar);
            String originalKey = jsonText(root, "originalKey");
            String minKey = jsonText(root, "minKey");
            String midKey = jsonText(root, "midKey");
            String status = jsonText(root, "status");
            if (status.isBlank()) {
                status = "READY";
            }
            Avatar avatar = Avatar.newBuilder()
                    .setOriginalKey(originalKey)
                    .setMinKey(minKey)
                    .setMidKey(midKey)
                    .setOriginalUrl(buildObjectUrl(originalKey))
                    .setMinUrl(buildObjectUrl(minKey))
                    .setMidUrl(buildObjectUrl(midKey))
                    .setStatus(status)
                    .build();
            if (avatar.getOriginalKey().isBlank()
                    && avatar.getMinKey().isBlank()
                    && avatar.getMidKey().isBlank()) {
                return null;
            }
            return avatar;
        } catch (Exception e) {
            log.warn("Invalid customAvatar json, user profile avatar ignored");
            return null;
        }
    }

    private Avatar toAvatarOrDefault(String customAvatar) {
        Avatar avatar = toAvatar(customAvatar);
        return avatar == null ? defaultAvatar() : avatar;
    }

    private Avatar defaultAvatar() {
        String defaultAvatarUrl = storageProperties.getDefaultAvatarUrl();
        if (defaultAvatarUrl == null || defaultAvatarUrl.isBlank()) {
            return null;
        }
        String normalizedUrl = defaultAvatarUrl.trim();
        return Avatar.newBuilder()
                .setOriginalUrl(normalizedUrl)
                .setMinUrl(normalizedUrl)
                .setMidUrl(normalizedUrl)
                .setStatus(AVATAR_STATUS_DEFAULT)
                .build();
    }

    private ProfileCompletion calculateProfileCompletion(UserInfoEntity user, Avatar avatar) {
        int score = 0;
        List<String> missingFields = new java.util.ArrayList<>();

        score += scoreText(user.getNickname(), "nickname", 15, missingFields);
        score += scorePositive(user.getGender(), "gender", 10, missingFields);
        score += scoreAgeOrBirthday(user, missingFields);
        score += scoreText(user.getBio(), "bio", 10, missingFields);
        score += scoreText(user.getPreferredLocation(), "location", 10, missingFields);
        score += scoreText(user.getProfession(), "occupation", 10, missingFields);
        score += scoreText(user.getEducation(), "education", 10, missingFields);
        score += scorePositive(user.getHeight(), "height", 10, missingFields);
        score += scoreAvatar(avatar, missingFields);

        return new ProfileCompletion(score, missingFields);
    }

    private int scoreText(String value, String fieldName, int score, List<String> missingFields) {
        if (value != null && !value.isBlank()) {
            return score;
        }
        missingFields.add(fieldName);
        return 0;
    }

    private int scorePositive(Integer value, String fieldName, int score, List<String> missingFields) {
        if (value != null && value > 0) {
            return score;
        }
        missingFields.add(fieldName);
        return 0;
    }

    private int scoreAgeOrBirthday(UserInfoEntity user, List<String> missingFields) {
        if ((user.getAge() != null && user.getAge() > 0) || user.getBirthday() != null) {
            return 10;
        }
        missingFields.add("birthday");
        return 0;
    }

    private int scoreAvatar(Avatar avatar, List<String> missingFields) {
        if (avatar != null
                && !AVATAR_STATUS_DEFAULT.equals(avatar.getStatus())
                && !avatar.getOriginalKey().isBlank()) {
            return 15;
        }
        missingFields.add("avatar");
        return 0;
    }

    private String buildObjectUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return "";
        }
        String baseUrl = storageProperties.getPublicBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = storageProperties.getEndpoint() + "/" + storageProperties.getBucket();
        }
        return trimTrailingSlash(baseUrl) + "/" + objectKey;
    }

    private String trimTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String jsonText(JsonNode root, String fieldName) {
        JsonNode node = root.get(fieldName);
        return node == null || node.isNull() ? "" : node.asText("");
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private record ProfileCompletion(
            int score,
            List<String> missingFields) {
    }

    private void handleException(Exception e, StreamObserver<?> responseObserver) {
        if (e instanceof IllegalArgumentException) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
            return;
        }

        log.error("UserProfileService gRPC call failed", e);
        responseObserver.onError(Status.INTERNAL
                .withDescription("internal server error")
                .asRuntimeException());
    }
}
