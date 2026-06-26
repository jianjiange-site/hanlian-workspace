package com.aurora.dating.post.service;

import com.aurora.dating.common.id.SnowflakeIdGenerator;
import com.dating.hanlian.proto.post.v1.Comment;
import com.aurora.dating.post.entity.PostCommentEntity;
import com.aurora.dating.post.entity.PostStatEntity;
import com.aurora.dating.post.exception.BusinessException;
import com.aurora.dating.post.manager.PostManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class PostCommentService {

    private final PostManager postManager;
    private final SnowflakeIdGenerator idGenerator;
    private final PostStatRedisService postStatRedisService;

    public PostCommentService(PostManager postManager
            , SnowflakeIdGenerator idGenerator
            , PostStatRedisService postStatRedisService) {
        this.postManager = postManager;
        this.idGenerator = idGenerator;
        this.postStatRedisService = postStatRedisService;
    }

    /**
     * 新建评论
     * @param userId
     * @param postId
     * @param content
     * @return
     */
    @Transactional
    public CreateCommentResult createComment(Long userId, Long postId, String content) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId is required");
        }
        if (postId == null || postId <= 0) {
            throw new IllegalArgumentException("postId is required");
        }
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("content is required");
        }

        String trimmedContent = content.trim();
        if (trimmedContent.length() > 512) {
            throw new IllegalArgumentException("content length must be <= 512");
        }

        if (!postManager.existsNormalPost(postId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "帖子不存在");
        }

        Long commentId = idGenerator.nextId();
        OffsetDateTime now = OffsetDateTime.now();

        PostCommentEntity comment = new PostCommentEntity();
        comment.setCommentId(commentId);
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setRootId(0L);
        comment.setParentId(0L);
        comment.setReplyToUserId(0L);
        comment.setContent(trimmedContent);
        comment.setStatus(1);
        comment.setDeleted(0);
        comment.setCreatedAt(now);

        postManager.createComment(comment);
        //postManager.increaseCommentCount(postId, 1);
        postStatRedisService.increaseCommentDelta(postId, 1);

        int dbCommentCount = postManager.findStatByPostId(postId)
                .map(PostStatEntity::getCommentCount)
                .orElse(0);
        int commentCount = dbCommentCount + postStatRedisService.getCommentDelta(postId);
        commentCount = Math.max(commentCount, 0);

        return new CreateCommentResult(commentId, commentCount);
    }

    /**
     * 查询评论
     * @param userId
     * @param postId
     * @param cursorCommentId
     * @param pageSize
     * @return
     */
    public ListCommentsResult listComments(Long userId, Long postId, Long cursorCommentId, int pageSize) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId is required");
        }
        if (postId == null || postId <= 0) {
            throw new IllegalArgumentException("postId is required");
        }
        int size = pageSize <= 0 ? 20 : Math.min(pageSize, 50);
        int queryLimit = size + 1;
        List<PostCommentEntity> entities = postManager
                .listRootCommentsByPostId(postId, cursorCommentId, queryLimit);
        boolean hasMore = entities.size() > size;
        if (hasMore){
            entities = entities.subList(0, size);
        }
        long nextCursorCommentId = entities.isEmpty() ? 0L : entities.get(entities.size() - 1).getCommentId();

        List<Comment> comments = entities.stream()
                .map(this::toProto)
                .toList();
        return new ListCommentsResult(comments, nextCursorCommentId, hasMore);
    }

    public record CreateCommentResult(
            long commentId,
            int commentCount
    ) {
    }

    public record ListCommentsResult(
            List<Comment> comments,
            long nextCursorCommentId,
            boolean hasMore
    ) {
    }

    /**
     * 转为grpc返回对象
     * @param entity
     * @return
     */
    private Comment toProto(PostCommentEntity entity) {
        return Comment.newBuilder()
                .setCommentId(entity.getCommentId())
                .setPostId(entity.getPostId())
                .setUserId(entity.getUserId())
                .setRootId(entity.getRootId() == null ? 0L : entity.getRootId())
                .setParentId(entity.getParentId() == null ? 0L : entity.getParentId())
                .setReplyToUserId(entity.getReplyToUserId() == null ? 0L : entity.getReplyToUserId())
                .setContent(entity.getContent() == null ? "" : entity.getContent())
                .setCreatedAt(toEpochSeconds(entity.getCreatedAt()))
                .build();
    }

    private long toEpochSeconds(OffsetDateTime time) {
        return time == null ? 0L : time.toEpochSecond();
    }
}
