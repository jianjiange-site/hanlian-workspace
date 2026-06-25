package com.jianjiange.dating.post.grpc;

import com.dating.hanlian.proto.post.v1.CreateCommentRequest;
import com.dating.hanlian.proto.post.v1.CreateCommentResponse;
import com.dating.hanlian.proto.post.v1.CreatePostRequest;
import com.dating.hanlian.proto.post.v1.CreatePostResponse;
import com.dating.hanlian.proto.post.v1.DeletePostRequest;
import com.dating.hanlian.proto.post.v1.DeletePostResponse;
import com.dating.hanlian.proto.post.v1.GetPostDetailRequest;
import com.dating.hanlian.proto.post.v1.GetPostDetailResponse;
import com.dating.hanlian.proto.post.v1.GetRecommendFeedRequest;
import com.dating.hanlian.proto.post.v1.GetRecommendFeedResponse;
import com.dating.hanlian.proto.post.v1.LikePostRequest;
import com.dating.hanlian.proto.post.v1.LikePostResponse;
import com.dating.hanlian.proto.post.v1.ListCommentsRequest;
import com.dating.hanlian.proto.post.v1.ListCommentsResponse;
import com.dating.hanlian.proto.post.v1.ListUserPostsRequest;
import com.dating.hanlian.proto.post.v1.ListUserPostsResponse;
import com.dating.hanlian.proto.post.v1.PingRequest;
import com.dating.hanlian.proto.post.v1.PingResponse;
import com.dating.hanlian.proto.post.v1.Post;
import com.dating.hanlian.proto.post.v1.PostServiceGrpc;
import com.jianjiange.dating.post.service.FeedService;
import com.jianjiange.dating.post.service.PostCommentService;
import com.jianjiange.dating.post.service.PostLikeService;
import com.jianjiange.dating.post.service.PostReadService;
import com.jianjiange.dating.post.service.PostWriteService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PostGrpcService extends PostServiceGrpc.PostServiceImplBase {

    private final PostWriteService postWriteService;
    private final PostReadService postReadService;
    private final PostLikeService postLikeService;
    private final PostCommentService postCommentService;
    private final FeedService feedService;

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        try {
            PingResponse response = PingResponse.newBuilder()
                    .setMessage("post-service pong: " + request.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            GrpcExceptionHandler.handle(e, responseObserver);
        }
    }


    @Override
    public void createPost(CreatePostRequest request, StreamObserver<CreatePostResponse> responseObserver) {
        try {
            Long postId = postWriteService.createPost(
                    request.getUserId(),
                    request.getContent(),
                    request.getImageKeysList()
            );

            CreatePostResponse response = CreatePostResponse.newBuilder()
                    .setPostId(postId)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            GrpcExceptionHandler.handle(e, responseObserver);
        }
    }

    @Override
    public void deletePost(DeletePostRequest request, StreamObserver<DeletePostResponse> responseObserver) {
        try {
            boolean success = postWriteService.deletePost(request.getUserId(), request.getPostId());
            DeletePostResponse response = DeletePostResponse.newBuilder()
                    .setSuccess(success)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            GrpcExceptionHandler.handle(e, responseObserver);
        }
    }

    @Override
    public void getPostDetail(GetPostDetailRequest request, StreamObserver<GetPostDetailResponse> responseObserver) {
        try {
            Post post = postReadService.getPostDetail(request.getUserId(), request.getPostId());
            GetPostDetailResponse response = GetPostDetailResponse.newBuilder()
                    .setPost(post)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            GrpcExceptionHandler.handle(e, responseObserver);
        }
    }

    @Override
    public void listUserPosts(ListUserPostsRequest request, StreamObserver<ListUserPostsResponse> responseObserver) {
        try {
            PostReadService.ListUserPostsResult result = postReadService.listUserPosts(
                    request.getViewerUserId(),
                    request.getTargetUserId(),
                    request.getCursorPostId(),
                    request.getPageSize()
            );

            ListUserPostsResponse response = ListUserPostsResponse.newBuilder()
                    .addAllPosts(result.posts())
                    .setNextCursorPostId(result.nextCursorPostId())
                    .setHasMore(result.hasMore())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            GrpcExceptionHandler.handle(e, responseObserver);
        }
    }

    @Override
    public void likePost(LikePostRequest request, StreamObserver<LikePostResponse> responseObserver) {
        try {
            PostLikeService.LikePostResult result = postLikeService.likePost(
                    request.getUserId(),
                    request.getPostId(),
                    request.getLiked()
            );

            LikePostResponse response = LikePostResponse.newBuilder()
                    .setSuccess(result.success())
                    .setLiked(result.liked())
                    .setLikeCount(result.likeCount())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            GrpcExceptionHandler.handle(e, responseObserver);
        }
    }

    @Override
    public void createComment(CreateCommentRequest request, StreamObserver<CreateCommentResponse> responseObserver) {
        try {
            PostCommentService.CreateCommentResult result = postCommentService.createComment(
                    request.getUserId(),
                    request.getPostId(),
                    request.getContent()
            );

            CreateCommentResponse response = CreateCommentResponse.newBuilder()
                    .setCommentId(result.commentId())
                    .setCommentCount(result.commentCount())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            GrpcExceptionHandler.handle(e, responseObserver);
        }
    }

    @Override
    public void listComments(ListCommentsRequest request, StreamObserver<ListCommentsResponse> responseObserver) {
        try {
            PostCommentService.ListCommentsResult result = postCommentService.listComments(
                    request.getUserId(),
                    request.getPostId(),
                    request.getCursorCommentId(),
                    request.getPageSize()
            );

            ListCommentsResponse response = ListCommentsResponse.newBuilder()
                    .addAllComments(result.comments())
                    .setNextCursorCommentId(result.nextCursorCommentId())
                    .setHasMore(result.hasMore())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            GrpcExceptionHandler.handle(e, responseObserver);
        }
    }

    @Override
    public void getRecommendFeed(GetRecommendFeedRequest request, StreamObserver<GetRecommendFeedResponse> responseObserver) {
        try {
            FeedService.RecommendFeedResult result = feedService.getRecommendFeed(
                    request.getUserId(),
                    request.getPageSize()
            );

            GetRecommendFeedResponse response = GetRecommendFeedResponse.newBuilder()
                    .addAllPosts(result.posts())
                    .setNextCursorPostId(result.nextCursorPostId())
                    .setHasMore(result.hasMore())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            GrpcExceptionHandler.handle(e, responseObserver);
        }
    }
}
