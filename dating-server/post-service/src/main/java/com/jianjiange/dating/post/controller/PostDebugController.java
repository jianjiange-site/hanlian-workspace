package com.jianjiange.dating.post.controller;

import com.dating.hanlian.proto.post.v1.Post;
import com.jianjiange.dating.post.service.PostLikeService;
import com.jianjiange.dating.post.service.PostReadService;
import com.jianjiange.dating.post.service.PostWriteService;
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
}
