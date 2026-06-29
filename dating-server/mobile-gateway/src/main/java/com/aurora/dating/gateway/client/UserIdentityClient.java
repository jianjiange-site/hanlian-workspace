package com.aurora.dating.gateway.client;

import com.aurora.dating.gateway.exception.BizException;
import com.aurora.dating.gateway.exception.ErrorCodes;
import com.dating.hanlian.proto.user.v1.BindAccountResponse;
import com.dating.hanlian.proto.user.v1.BindPhoneRequest;
import com.dating.hanlian.proto.user.v1.BindThirdPartyRequest;
import com.dating.hanlian.proto.user.v1.CheckBanRequest;
import com.dating.hanlian.proto.user.v1.CheckBanResponse;
import com.dating.hanlian.proto.user.v1.ResolveOrCreateByDeviceRequest;
import com.dating.hanlian.proto.user.v1.ResolveOrCreateByPhoneRequest;
import com.dating.hanlian.proto.user.v1.ResolveOrCreateByThirdPartyRequest;
import com.dating.hanlian.proto.user.v1.ResolveOrCreateResponse;
import com.dating.hanlian.proto.user.v1.UserIdentityServiceGrpc;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Component;

@Component
public class UserIdentityClient {

    private final UserIdentityServiceGrpc.UserIdentityServiceBlockingStub userIdentityServiceBlockingStub;

    public UserIdentityClient(
            UserIdentityServiceGrpc.UserIdentityServiceBlockingStub userIdentityServiceBlockingStub) {
        this.userIdentityServiceBlockingStub = userIdentityServiceBlockingStub;
    }

    public ResolveOrCreateResponse resolveOrCreateByPhone(String phoneE164, String appName) {
        ResolveOrCreateByPhoneRequest request = ResolveOrCreateByPhoneRequest.newBuilder()
                .setPhoneE164(phoneE164)
                .setAppName(appName)
                .build();
        try {
            return userIdentityServiceBlockingStub.resolveOrCreateByPhone(request);
        } catch (StatusRuntimeException e) {
            throw toBizException(e);
        }
    }

    public ResolveOrCreateResponse resolveOrCreateByDevice(String deviceId, int platform, String appName) {
        ResolveOrCreateByDeviceRequest request = ResolveOrCreateByDeviceRequest.newBuilder()
                .setDeviceId(deviceId)
                .setPlatformValue(platform)
                .setAppName(appName)
                .build();
        try {
            return userIdentityServiceBlockingStub.resolveOrCreateByDevice(request);
        } catch (StatusRuntimeException e) {
            throw toBizException(e);
        }
    }

    public ResolveOrCreateResponse resolveOrCreateByThirdParty(
            int platform,
            String thirdPartyUserId,
            String appName,
            String email) {
        ResolveOrCreateByThirdPartyRequest request = ResolveOrCreateByThirdPartyRequest.newBuilder()
                .setPlatformValue(platform)
                .setThirdPartyUserId(thirdPartyUserId)
                .setAppName(appName)
                .setEmail(email == null ? "" : email)
                .build();
        try {
            return userIdentityServiceBlockingStub.resolveOrCreateByThirdParty(request);
        } catch (StatusRuntimeException e) {
            throw toBizException(e);
        }
    }

    public CheckBanResponse checkBan(long userId) {
        CheckBanRequest request = CheckBanRequest.newBuilder()
                .setUserId(userId)
                .build();
        try {
            return userIdentityServiceBlockingStub.checkBan(request);
        } catch (StatusRuntimeException e) {
            throw toBizException(e);
        }
    }

    public BindAccountResponse bindPhone(long userId, String phoneE164, String appName) {
        BindPhoneRequest request = BindPhoneRequest.newBuilder()
                .setUserId(userId)
                .setPhoneE164(phoneE164)
                .setAppName(appName)
                .build();
        try {
            return userIdentityServiceBlockingStub.bindPhone(request);
        } catch (StatusRuntimeException e) {
            throw toBizException(e);
        }
    }

    public BindAccountResponse bindThirdParty(
            long userId,
            int platform,
            String thirdPartyUserId,
            String appName,
            String email) {
        BindThirdPartyRequest request = BindThirdPartyRequest.newBuilder()
                .setUserId(userId)
                .setPlatformValue(platform)
                .setThirdPartyUserId(thirdPartyUserId)
                .setAppName(appName)
                .setEmail(email == null ? "" : email)
                .build();
        try {
            return userIdentityServiceBlockingStub.bindThirdParty(request);
        } catch (StatusRuntimeException e) {
            throw toBizException(e);
        }
    }

    private BizException toBizException(StatusRuntimeException e) {
        String message = e.getStatus().getDescription();
        if (message == null || message.isBlank()) {
            message = "upstream user-service unavailable";
        }
        return new BizException(ErrorCodes.UPSTREAM_UNAVAILABLE, message);
    }
}
