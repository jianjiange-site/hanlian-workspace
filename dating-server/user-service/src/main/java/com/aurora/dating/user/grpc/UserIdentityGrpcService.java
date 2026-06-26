package com.aurora.dating.user.grpc;

import com.aurora.dating.user.entity.UserInfoEntity;
import com.aurora.dating.user.service.UserIdentityService;
import com.dating.hanlian.proto.user.v1.CheckBanRequest;
import com.dating.hanlian.proto.user.v1.CheckBanResponse;
import com.dating.hanlian.proto.user.v1.ResolveOrCreateByDeviceRequest;
import com.dating.hanlian.proto.user.v1.ResolveOrCreateByPhoneRequest;
import com.dating.hanlian.proto.user.v1.ResolveOrCreateByThirdPartyRequest;
import com.dating.hanlian.proto.user.v1.ResolveOrCreateResponse;
import com.dating.hanlian.proto.user.v1.UserIdentityServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UserIdentityGrpcService extends UserIdentityServiceGrpc.UserIdentityServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(UserIdentityGrpcService.class);

    private final UserIdentityService userIdentityService;

    public UserIdentityGrpcService(UserIdentityService userIdentityService) {
        this.userIdentityService = userIdentityService;
    }

    /**
     * 处理手机号登录/创建用户的 gRPC 请求
     * @param request
     * @param responseObserver
     */
    @Override
    public void resolveOrCreateByPhone(
            ResolveOrCreateByPhoneRequest request,
            StreamObserver<ResolveOrCreateResponse> responseObserver) {
        try {
            UserInfoEntity user = userIdentityService.resolveOrCreateByPhone(
                    request.getPhoneE164(),
                    request.getAppName());

            ResolveOrCreateResponse response = ResolveOrCreateResponse.newBuilder()
                    .setUserId(user.getUserId())
                    .setPending(Boolean.TRUE.equals(user.getPending()))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleException(e, responseObserver);
        }
    }

    /**
     * 处理设备登录/创建用户
     * @param request
     * @param responseObserver
     */
    @Override
    public void resolveOrCreateByDevice(
            ResolveOrCreateByDeviceRequest request,
            StreamObserver<ResolveOrCreateResponse> responseObserver) {
        try {
            UserInfoEntity user = userIdentityService.resolveOrCreateByDevice(
                    request.getDeviceId(),
                    request.getPlatformValue(),
                    request.getAppName());

            ResolveOrCreateResponse response = ResolveOrCreateResponse.newBuilder()
                    .setUserId(user.getUserId())
                    .setPending(Boolean.TRUE.equals(user.getPending()))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleException(e, responseObserver);
        }
    }

    @Override
    public void resolveOrCreateByThirdParty(
            ResolveOrCreateByThirdPartyRequest request,
            StreamObserver<ResolveOrCreateResponse> responseObserver) {
        responseObserver.onError(Status.UNIMPLEMENTED
                .withDescription("ResolveOrCreateByThirdParty is not implemented yet")
                .asRuntimeException());
    }

    /**
     * 检查用户是否封禁
     * @param request
     * @param responseObserver
     */
    @Override
    public void checkBan(
            CheckBanRequest request,
            StreamObserver<CheckBanResponse> responseObserver) {
        try {
            boolean banned = userIdentityService.isBanned(request.getUserId());

            CheckBanResponse response = CheckBanResponse.newBuilder()
                    .setBanned(banned)
                    .setReason(banned ? "USER_BANNED" : "")
                    .setBannedAtMs(0)
                    .setMessage(banned ? "用户已被封禁" : "")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleException(e, responseObserver);
        }
    }

    /**
     * 统一把 Java 异常转成 gRPC 错误
     * @param e
     * @param responseObserver
     */
    private void handleException(Exception e, StreamObserver<?> responseObserver) {
        if (e instanceof IllegalArgumentException) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
            return;
        }

        log.error("UserIdentityService gRPC call failed", e);
        responseObserver.onError(Status.INTERNAL
                .withDescription("internal server error")
                .asRuntimeException());
    }
}
