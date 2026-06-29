package com.aurora.dating.gateway.controller;

import com.aurora.dating.gateway.client.UserProfileClient;
import com.aurora.dating.gateway.dto.BatchGetProfilesRequest;
import com.aurora.dating.gateway.dto.ConfirmAvatarUploadRequest;
import com.aurora.dating.gateway.dto.OnboardingRequest;
import com.aurora.dating.gateway.dto.PresignAvatarUploadRequest;
import com.aurora.dating.gateway.dto.ReplaceUserInterestsRequest;
import com.aurora.dating.gateway.dto.UpdateProfileRequest;
import com.aurora.dating.gateway.security.JwtUserContext;
import com.aurora.dating.gateway.vo.BatchGetProfilesResponse;
import com.aurora.dating.gateway.vo.PresignAvatarUploadResponse;
import com.aurora.dating.gateway.vo.Result;
import com.aurora.dating.gateway.vo.UserInterestResponse;
import com.aurora.dating.gateway.vo.UserInterestsResponse;
import com.aurora.dating.gateway.vo.UserProfileResponse;
import com.dating.hanlian.proto.user.v1.UserInterest;
import com.dating.hanlian.proto.user.v1.UserProfile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.List;

@RestController
@RequestMapping("/api/v1/user")
public class UserProfileController {

    private final UserProfileClient userProfileClient;

    public UserProfileController(UserProfileClient userProfileClient) {
        this.userProfileClient = userProfileClient;
    }

    @GetMapping("/me")
    public Result<UserProfileResponse> me() {
        Long userId = JwtUserContext.getUserId();
        UserProfile profile = userProfileClient.getProfile(userId);
        return Result.success(UserProfileResponse.from(profile));
    }

    @PostMapping("/profiles/batch")
    public Result<BatchGetProfilesResponse> batchGetProfiles(@RequestBody BatchGetProfilesRequest request) {
        List<UserProfileResponse> profiles = userProfileClient.batchGetProfile(request.userIds()).stream()
                .map(UserProfileResponse::from)
                .toList();
        return Result.success(new BatchGetProfilesResponse(profiles));
    }

    @PostMapping("/onboarding")
    public Result<UserProfileResponse> onboarding(@RequestBody OnboardingRequest request) {
        Long userId = JwtUserContext.getUserId();
        UserProfile profile = userProfileClient.upsertOnboarding(
                userId,
                request.nickname(),
                request.gender(),
                request.age(),
                request.birthday(),
                request.bio(),
                request.location(),
                request.occupation(),
                request.education(),
                request.height());
        return Result.success(UserProfileResponse.from(profile));
    }

    @PostMapping("/avatar/presign")
    public Result<PresignAvatarUploadResponse> presignAvatarUpload(
            @RequestBody PresignAvatarUploadRequest request) {
        Long userId = JwtUserContext.getUserId();
        com.dating.hanlian.proto.user.v1.PresignAvatarUploadResponse response =
                userProfileClient.presignAvatarUpload(
                        userId,
                        request.fileExt(),
                        request.contentType(),
                        request.contentLength());
        return Result.success(new PresignAvatarUploadResponse(
                response.getUploadUrl(),
                response.getObjectKey(),
                response.getExpireSeconds()));
    }

    @PostMapping("/avatar/confirm")
    public Result<UserProfileResponse> confirmAvatarUpload(@RequestBody ConfirmAvatarUploadRequest request) {
        Long userId = JwtUserContext.getUserId();
        UserProfile profile = userProfileClient.confirmAvatarUpload(userId, request.objectKey());
        return Result.success(UserProfileResponse.from(profile));
    }

    @PutMapping("/me")
    public Result<UserProfileResponse> updateMe(@RequestBody UpdateProfileRequest request) {
        Long userId = JwtUserContext.getUserId();
        UserProfile profile = userProfileClient.updateProfile(
                userId,
                request.nickname(),
                request.age(),
                request.bio(),
                request.location(),
                request.occupation(),
                request.education(),
                request.height());
        return Result.success(UserProfileResponse.from(profile));
    }

    @GetMapping("/interests")
    public Result<UserInterestsResponse> getInterests() {
        Long userId = JwtUserContext.getUserId();
        List<UserInterestResponse> interests = userProfileClient.getUserInterests(userId).stream()
                .map(UserInterestResponse::from)
                .toList();
        return Result.success(new UserInterestsResponse(interests));
    }

    @PutMapping("/interests")
    public Result<UserInterestsResponse> replaceInterests(@RequestBody ReplaceUserInterestsRequest request) {
        Long userId = JwtUserContext.getUserId();
        List<UserInterest> interests = userProfileClient.replaceUserInterests(userId, request.interests());
        return Result.success(new UserInterestsResponse(interests.stream()
                .map(UserInterestResponse::from)
                .toList()));
    }
}
