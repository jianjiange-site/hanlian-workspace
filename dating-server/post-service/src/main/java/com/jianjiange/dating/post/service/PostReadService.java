package com.jianjiange.dating.post.service;

import com.dating.hanlian.proto.post.v1.Post;
import com.jianjiange.dating.post.entity.PostEntity;
import com.jianjiange.dating.post.entity.PostImageEntity;
import com.jianjiange.dating.post.entity.PostStatEntity;
import com.jianjiange.dating.post.manager.PostManager;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class PostReadService {

    private final PostManager postManager;

    public PostReadService(PostManager postManager) {
        this.postManager = postManager;
    }

    /**
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
                .orElseThrow(() -> new IllegalArgumentException("post not found"));
        List<PostImageEntity> images = postManager.listImagesByPostId(postId);
        PostStatEntity stat = postManager.findStatByPostId(postId).orElse(null);

        Post.Builder builder = Post.newBuilder()
                .setPostId(post.getPostId())
                .setUserId(post.getUserId())
                .setContent(post.getContent())
                .setLikeCount(stat == null ? 0 : stat.getLikeCount())
                .setCommentCount(stat == null ? 0 : stat.getCommentCount())
                .setLikedByMe(false)
                .setCreatedAt(toEpochMillis(post.getCreatedAt()));

        for (PostImageEntity image : images) {
            builder.addImageKeys(image.getImageKey());
        }

        return builder.build();
    }

    private long toEpochMillis(OffsetDateTime time) {
        return time == null ? 0L : time.toInstant().toEpochMilli();
    }
}
