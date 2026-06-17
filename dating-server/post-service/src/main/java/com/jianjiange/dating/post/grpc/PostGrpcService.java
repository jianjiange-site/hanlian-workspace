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
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;
import com.jianjiange.dating.post.service.PostReadService;
import com.jianjiange.dating.post.service.PostWriteService;

@Component
public class PostGrpcService extends PostServiceGrpc.PostServiceImplBase {

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        PingResponse response = PingResponse.newBuilder()
                .setMessage("post-service pong: " + request.getMessage())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }


    @Override
    public void createPost(CreatePostRequest request, StreamObserver<CreatePostResponse> responseObserver) {
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
    }

    @Override
    public void deletePost(DeletePostRequest request, StreamObserver<DeletePostResponse> responseObserver) {
        boolean success = postWriteService.deletePost(request.getUserId(), request.getPostId());
        DeletePostResponse response = DeletePostResponse.newBuilder()
                .setSuccess(success)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getPostDetail(GetPostDetailRequest request, StreamObserver<GetPostDetailResponse> responseObserver) {
        Post post = postReadService.getPostDetail(request.getUserId(), request.getPostId());
        GetPostDetailResponse response = GetPostDetailResponse.newBuilder()
                .setPost(post)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void listUserPosts(ListUserPostsRequest request, StreamObserver<ListUserPostsResponse> responseObserver) {
        ListUserPostsResponse response = ListUserPostsResponse.newBuilder()
                .setHasMore(false)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void likePost(LikePostRequest request, StreamObserver<LikePostResponse> responseObserver) {
        LikePostResponse response = LikePostResponse.newBuilder()
                .setSuccess(false)
                .setLiked(request.getLiked())
                .setLikeCount(0)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void createComment(CreateCommentRequest request, StreamObserver<CreateCommentResponse> responseObserver) {
        CreateCommentResponse response = CreateCommentResponse.newBuilder()
                .setCommentId(0L)
                .setCommentCount(0)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void listComments(ListCommentsRequest request, StreamObserver<ListCommentsResponse> responseObserver) {
        ListCommentsResponse response = ListCommentsResponse.newBuilder()
                .setHasMore(false)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getRecommendFeed(GetRecommendFeedRequest request, StreamObserver<GetRecommendFeedResponse> responseObserver) {
        GetRecommendFeedResponse response = GetRecommendFeedResponse.newBuilder().build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private final PostWriteService postWriteService;
    private final PostReadService postReadService;

    public PostGrpcService(PostWriteService postWriteService, PostReadService postReadService) {
        this.postWriteService = postWriteService;
        this.postReadService = postReadService;
    }
}
