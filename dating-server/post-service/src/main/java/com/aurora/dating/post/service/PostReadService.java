package com.aurora.dating.post.service;

import com.dating.hanlian.proto.post.v1.Post;
import com.aurora.dating.post.entity.PostEntity;
import com.aurora.dating.post.entity.PostImageEntity;
import com.aurora.dating.post.entity.PostStatEntity;
import com.aurora.dating.post.exception.BusinessException;
import com.aurora.dating.post.manager.PostManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class PostReadService {

    private final PostManager postManager;
    private final PostStatRedisService postStatRedisService;

    public PostReadService(PostManager postManager, PostStatRedisService postStatRedisService) {
        this.postManager = postManager;
        this.postStatRedisService = postStatRedisService;
    }

    /**
     * 查询一条帖子信息
     *
     * 分别查询帖子信息，图片，点赞评论并封装到PostEntity返回
     * @param viewerUserId
     * @param postId
     * @return
     */
    public Post getPostDetail(Long viewerUserId, Long postId) {
        if (viewerUserId == null || viewerUserId <= 0) {
            throw new IllegalArgumentException("viewerUserId is required");
        }
        if (postId == null || postId <= 0) {
            throw new IllegalArgumentException("postId is required");
        }

        PostEntity post = postManager.findNormalPostByPostId(postId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "帖子不存在"));
        List<PostImageEntity> images = postManager.listImagesByPostId(postId);
        PostStatEntity stat = postManager.findStatByPostId(postId).orElse(null);
        boolean likedByMe = postManager.findLikeByUserIdAndPostId(viewerUserId, postId)
                .map(like -> Integer.valueOf(1).equals(like.getStatus()))
                .orElse(false);

        int dbLikeCount = stat == null ? 0 : stat.getLikeCount();
        int dbCommentCount = stat == null ? 0 : stat.getCommentCount();

        int likeCount = dbLikeCount + postStatRedisService.getLikeDelta(postId);
        int commentCount = dbCommentCount + postStatRedisService.getCommentDelta(postId);

        Post.Builder builder = Post.newBuilder()
                .setPostId(post.getPostId())
                .setUserId(post.getUserId())
                .setContent(post.getContent())
                .setLikeCount(Math.max(likeCount, 0))
                .setCommentCount(Math.max(commentCount, 0))
                .setLikedByMe(likedByMe)
                .setCreatedAt(toEpochMillis(post.getCreatedAt()));

        for (PostImageEntity image : images) {
            builder.addImageKeys(image.getImageKey());
        }

        return builder.build();
    }

    private long toEpochMillis(OffsetDateTime time) {
        return time == null ? 0L : time.toInstant().toEpochMilli();
    }

    /**
     * 分页查询帖子信息
     *
     * 利用游标来定位帖子位置
     * @param viewerUserId
     * @param targetUserId
     * @param cursorPostId
     * @param pageSize
     * @return
     */
    public ListUserPostsResult listUserPosts(Long viewerUserId, Long targetUserId, Long cursorPostId, int pageSize) {
        if (viewerUserId == null || viewerUserId <= 0) {
            throw new IllegalArgumentException("viewerUserId is required");
        }
        if (targetUserId == null || targetUserId <= 0) {
            throw new IllegalArgumentException("targetUserId is required");
        }

        int size = pageSize <= 0 ? 20 : Math.min(pageSize, 50);
        int queryLimit = size + 1;

        List<PostEntity> entities = postManager.listNormalPostsByUserId(targetUserId, cursorPostId, queryLimit);

        boolean hasMore = entities.size() > size;
        if (hasMore) {
            entities = entities.subList(0, size);
        }

        List<Post> posts = entities.stream()
                .map(entity -> getPostDetail(viewerUserId, entity.getPostId()))
                .toList();

        long nextCursorPostId = entities.isEmpty()
                ? 0L
                : entities.get(entities.size() - 1).getPostId();

        return new ListUserPostsResult(posts, nextCursorPostId, hasMore);
    }

    public record ListUserPostsResult(
            List<Post> posts,
            long nextCursorPostId,
            boolean hasMore
    ) {
    }
}
