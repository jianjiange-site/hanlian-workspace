package com.aurora.dating.im.grpc;

import com.aurora.dating.im.service.CallTokenService;
import com.aurora.dating.im.service.CallbackService;
import com.aurora.dating.im.service.ConversationService;
import com.aurora.dating.im.service.ImUserService;
import com.aurora.dating.im.service.PresenceService;
import com.aurora.dating.im.service.SystemMessageService;
import com.dating.hanlian.proto.im.v1.EnsureConversationRequest;
import com.dating.hanlian.proto.im.v1.EnsureConversationResponse;
import com.dating.hanlian.proto.im.v1.GenerateCallTokenRequest;
import com.dating.hanlian.proto.im.v1.GenerateCallTokenResponse;
import com.dating.hanlian.proto.im.v1.GetImTokenRequest;
import com.dating.hanlian.proto.im.v1.GetImTokenResponse;
import com.dating.hanlian.proto.im.v1.ImServiceGrpc;
import com.dating.hanlian.proto.im.v1.ListOnlineUserIdsRequest;
import com.dating.hanlian.proto.im.v1.ListOnlineUserIdsResponse;
import com.dating.hanlian.proto.im.v1.ListRecentOfflineUsersRequest;
import com.dating.hanlian.proto.im.v1.ListRecentOfflineUsersResponse;
import com.dating.hanlian.proto.im.v1.OnRawCallbackRequest;
import com.dating.hanlian.proto.im.v1.OnRawCallbackResponse;
import com.dating.hanlian.proto.im.v1.PingRequest;
import com.dating.hanlian.proto.im.v1.PingResponse;
import com.dating.hanlian.proto.im.v1.RegisterImUserRequest;
import com.dating.hanlian.proto.im.v1.RegisterImUserResponse;
import com.dating.hanlian.proto.im.v1.SendSystemMessageRequest;
import com.dating.hanlian.proto.im.v1.SendSystemMessageResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

@Component
public class ImGrpcService extends ImServiceGrpc.ImServiceImplBase {

    private final CallbackService callbackService;
    private final ImUserService imUserService;
    private final ConversationService conversationService;
    private final SystemMessageService systemMessageService;
    private final CallTokenService callTokenService;
    private final PresenceService presenceService;

    public ImGrpcService(
            CallbackService callbackService,
            ImUserService imUserService,
            ConversationService conversationService,
            SystemMessageService systemMessageService,
            CallTokenService callTokenService,
            PresenceService presenceService) {
        this.callbackService = callbackService;
        this.imUserService = imUserService;
        this.conversationService = conversationService;
        this.systemMessageService = systemMessageService;
        this.callTokenService = callTokenService;
        this.presenceService = presenceService;
    }

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        try {
            PingResponse response = PingResponse.newBuilder()
                    .setMessage("im-service pong: " + request.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handle(e, responseObserver);
        }
    }

    @Override
    public void onRawCallback(
            OnRawCallbackRequest request,
            StreamObserver<OnRawCallbackResponse> responseObserver) {
        try {
            CallbackService.CallbackResult result = callbackService.handle(
                    request.getProvider(),
                    request.getPayload().toByteArray());
            OnRawCallbackResponse response = OnRawCallbackResponse.newBuilder()
                    .setCode(result.code())
                    .setMessage(result.message())
                    .setAllow(result.allow())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handle(e, responseObserver);
        }
    }

    @Override
    public void registerImUser(
            RegisterImUserRequest request,
            StreamObserver<RegisterImUserResponse> responseObserver) {
        try {
            ImUserService.RegisterResult result = imUserService.register(
                    request.getUserId(),
                    request.getNickname(),
                    request.getAvatar());
            RegisterImUserResponse response = RegisterImUserResponse.newBuilder()
                    .setSuccess(result.success())
                    .setImUserId(result.imUserId())
                    .setMessage(result.message())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handle(e, responseObserver);
        }
    }

    @Override
    public void getImToken(
            GetImTokenRequest request,
            StreamObserver<GetImTokenResponse> responseObserver) {
        try {
            ImUserService.TokenResult result = imUserService.getToken(
                    request.getUserId(),
                    request.getPlatform());
            GetImTokenResponse response = GetImTokenResponse.newBuilder()
                    .setImUserId(result.imUserId())
                    .setToken(result.token())
                    .setExpireAtMs(result.expireAtMs())
                    .setMock(result.mock())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handle(e, responseObserver);
        }
    }

    @Override
    public void ensureConversation(
            EnsureConversationRequest request,
            StreamObserver<EnsureConversationResponse> responseObserver) {
        try {
            ConversationService.ConversationResult result = conversationService.ensureConversation(
                    request.getUserIdA(),
                    request.getUserIdB());
            EnsureConversationResponse response = EnsureConversationResponse.newBuilder()
                    .setSuccess(result.success())
                    .setConversationId(result.conversationId())
                    .setMessage(result.message())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handle(e, responseObserver);
        }
    }

    @Override
    public void sendSystemMessage(
            SendSystemMessageRequest request,
            StreamObserver<SendSystemMessageResponse> responseObserver) {
        try {
            SystemMessageService.SendResult result = systemMessageService.sendSystemMessage(
                    request.getToUserId(),
                    request.getBizType(),
                    request.getTitle(),
                    request.getContent(),
                    request.getPayloadJson());
            SendSystemMessageResponse response = SendSystemMessageResponse.newBuilder()
                    .setSuccess(result.success())
                    .setMessageId(result.messageId())
                    .setMessage(result.message())
                    .setMock(result.mock())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handle(e, responseObserver);
        }
    }

    @Override
    public void generateCallToken(
            GenerateCallTokenRequest request,
            StreamObserver<GenerateCallTokenResponse> responseObserver) {
        try {
            CallTokenService.CallTokenResult result = callTokenService.generate(
                    request.getUserId(),
                    request.getPeerUserId());
            GenerateCallTokenResponse response = GenerateCallTokenResponse.newBuilder()
                    .setRoomId(result.roomId())
                    .setToken(result.token())
                    .setExpireAtMs(result.expireAtMs())
                    .setMock(result.mock())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handle(e, responseObserver);
        }
    }

    @Override
    public void listOnlineUserIds(
            ListOnlineUserIdsRequest request,
            StreamObserver<ListOnlineUserIdsResponse> responseObserver) {
        try {
            ListOnlineUserIdsResponse response = ListOnlineUserIdsResponse.newBuilder()
                    .addAllUserIds(presenceService.listOnlineUserIds(
                            request.getSinceMs(),
                            request.getUntilMs(),
                            request.getLimit()))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handle(e, responseObserver);
        }
    }

    @Override
    public void listRecentOfflineUsers(
            ListRecentOfflineUsersRequest request,
            StreamObserver<ListRecentOfflineUsersResponse> responseObserver) {
        try {
            ListRecentOfflineUsersResponse response = ListRecentOfflineUsersResponse.newBuilder()
                    .addAllUserIds(presenceService.listRecentOfflineUsers(
                            request.getSinceMs(),
                            request.getUntilMs(),
                            request.getLimit()))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handle(e, responseObserver);
        }
    }

    private void handle(Exception e, StreamObserver<?> responseObserver) {
        Status status = e instanceof IllegalArgumentException
                ? Status.INVALID_ARGUMENT
                : Status.INTERNAL;
        responseObserver.onError(status.withDescription(e.getMessage()).withCause(e).asRuntimeException());
    }
}
