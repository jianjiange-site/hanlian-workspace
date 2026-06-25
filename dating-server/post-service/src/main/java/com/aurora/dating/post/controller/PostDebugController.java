package com.aurora.dating.post.controller;

import com.dating.hanlian.proto.post.v1.Comment;
import com.dating.hanlian.proto.post.v1.Post;
import com.aurora.dating.post.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/debug/posts")
@RequiredArgsConstructor
public class PostDebugController {

    private final PostReadService postReadService;
    private final PostWriteService postWriteService;
    private final PostLikeService postLikeService;
    private final PostCommentService postCommentService;
    private final FeedService feedService;

    /**
     * 新建帖子
     * @param request
     * @return
     */
    @PostMapping
    public CreatePostDebugResponse createPost(@RequestBody CreatePostDebugRequest request) {
        Long postId = postWriteService.createPost(
                request.userId(),
                request.content(),
                request.imageKeys() == null ? List.of() : request.imageKeys()
        );

        return new CreatePostDebugResponse(postId);
    }

    /**
     * 查看帖子
     * @param postId
     * @param userId
     * @return
     */
    @GetMapping("/{postId}")
    public PostDebugDetailResponse getPostDetail(@PathVariable("postId") Long postId,
                                                 @RequestParam("userId") Long userId) {
        Post post = postReadService.getPostDetail(userId, postId);
        return new PostDebugDetailResponse(
                post.getPostId(),
                post.getUserId(),
                post.getContent(),
                post.getImageKeysList(),
                post.getLikeCount(),
                post.getCommentCount(),
                post.getLikedByMe(),
                post.getCreatedAt()
        );
    }

    /**
     * 删除帖子
     * @param postId
     * @param userId
     * @return
     */
    @DeleteMapping("/{postId}")
    public DeletePostDebugResponse deletePost(@PathVariable("postId") Long postId,
                                              @RequestParam("userId") Long userId) {
        boolean success = postWriteService.deletePost(userId, postId);
        return new DeletePostDebugResponse(success);
    }


    @PostMapping("/{postId}/like")
    public LikePostDebugResponse likePost(@PathVariable("postId") Long postId,
                                          @RequestParam("userId") Long userId,
                                          @RequestParam("liked") boolean liked) {
        PostLikeService.LikePostResult result = postLikeService.likePost(userId, postId, liked);
        return new LikePostDebugResponse(result.success(), result.liked(), result.likeCount());
    }

    @PostMapping("/{postId}/comments")
    public CreateCommentDebugResponse createComment(@PathVariable("postId") Long postId,
                                                    @RequestBody CreateCommentDebugRequest request) {
        PostCommentService.CreateCommentResult result = postCommentService.createComment(
                request.userId(),
                postId,
                request.content()
        );

        return new CreateCommentDebugResponse(result.commentId(), result.commentCount());
    }

    @GetMapping("/{postId}/comments")
    public ListCommentsDebugResponse listComments(@PathVariable("postId") Long postId,
                                                  @RequestParam("userId") Long userId,
                                                  @RequestParam(value = "cursor", defaultValue = "0") Long cursor,
                                                  @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        PostCommentService.ListCommentsResult result = postCommentService.listComments(
                userId,
                postId,
                cursor,
                pageSize
        );

        List<CommentDebugResponse> comments = result.comments().stream()
                .map(this::toDebugComment)
                .toList();

        return new ListCommentsDebugResponse(comments, result.nextCursorCommentId(), result.hasMore());
    }

    private CommentDebugResponse toDebugComment(Comment comment) {
        return new CommentDebugResponse(
                comment.getCommentId(),
                comment.getPostId(),
                comment.getUserId(),
                comment.getRootId(),
                comment.getParentId(),
                comment.getReplyToUserId(),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }

    public record CreatePostDebugRequest(
            Long userId,
            String content,
            List<String> imageKeys
    ) {
    }

    public record CreatePostDebugResponse(
            Long postId
    ) {
    }

    public record PostDebugDetailResponse(
            Long postId,
            Long userId,
            String content,
            List<String> imageKeys,
            int likeCount,
            int commentCount,
            boolean likedByMe,
            long createdAt
    ) {
    }

    public record DeletePostDebugResponse(
            boolean success
    ) {
    }

    public record LikePostDebugResponse(
            boolean success,
            boolean liked,
            int likeCount
    ) {
    }

    public record CreateCommentDebugRequest(
            Long userId,
            String content
    ) {
    }

    public record CreateCommentDebugResponse(
            Long commentId,
            int commentCount
    ) {
    }

    public record CommentDebugResponse(
            Long commentId,
            Long postId,
            Long userId,
            Long rootId,
            Long parentId,
            Long replyToUserId,
            String content,
            long createdAt
    ) {
    }

    public record ListCommentsDebugResponse(
            List<CommentDebugResponse> comments,
            long nextCursorCommentId,
            boolean hasMore
    ) {
    }

    @GetMapping("/user/{targetUserId}")
    public ListUserPostsDebugResponse listUserPosts(@PathVariable("targetUserId") Long targetUserId,
                                                    @RequestParam("viewerUserId") Long viewerUserId,
                                                    @RequestParam(value = "cursor", defaultValue = "0") Long cursor,
                                                    @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        PostReadService.ListUserPostsResult result = postReadService.listUserPosts(
                viewerUserId,
                targetUserId,
                cursor,
                pageSize
        );

        return new ListUserPostsDebugResponse(
                result.posts().stream()
                        .map(this::toDebugPost)
                        .toList(),
                result.nextCursorPostId(),
                result.hasMore()
        );
    }

    private PostDebugDetailResponse toDebugPost(Post post) {
        return new PostDebugDetailResponse(
                post.getPostId(),
                post.getUserId(),
                post.getContent(),
                post.getImageKeysList(),
                post.getLikeCount(),
                post.getCommentCount(),
                post.getLikedByMe(),
                post.getCreatedAt()
        );
    }

    public record ListUserPostsDebugResponse(
            List<PostDebugDetailResponse> posts,
            long nextCursorPostId,
            boolean hasMore
    ) {
    }

    @GetMapping("/feed")
    public FeedDebugResponse getRecommendFeed(@RequestParam("userId") Long userId,
                                              @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        FeedService.RecommendFeedResult result = feedService.getRecommendFeed(userId, pageSize);

        List<PostDebugDetailResponse> posts = result.posts()
                .stream()
                .map(this::toDebugPost)
                .toList();

        return new FeedDebugResponse(
                posts,
                result.nextCursorPostId(),
                result.hasMore()
        );
    }

    public record FeedDebugResponse(
            List<PostDebugDetailResponse> posts,
            long nextCursorPostId,
            boolean hasMore
    ) {
    }


}
