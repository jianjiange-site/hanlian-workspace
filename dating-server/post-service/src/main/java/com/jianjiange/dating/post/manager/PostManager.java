package com.jianjiange.dating.post.manager;

import com.jianjiange.dating.post.entity.PostEntity;
import com.jianjiange.dating.post.entity.PostImageEntity;
import com.jianjiange.dating.post.entity.PostStatEntity;
import com.jianjiange.dating.post.mapper.PostImageMapper;
import com.jianjiange.dating.post.mapper.PostMapper;
import com.jianjiange.dating.post.mapper.PostStatMapper;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;


@Component
public class PostManager {

    private final PostMapper postMapper;
    private final PostImageMapper postImageMapper;
    private final PostStatMapper postStatMapper;

    public PostManager(PostMapper postMapper,
                       PostImageMapper postImageMapper,
                       PostStatMapper postStatMapper) {
        this.postMapper = postMapper;
        this.postImageMapper = postImageMapper;
        this.postStatMapper = postStatMapper;
    }

    /**
     * 1.创建PostEntity赋值
     * 2.在帖子存在多张图片的时候，遍历图片给PostImageEntity赋值
     * 3.创建PostStatEntity并初始化点赞评论
     * 4.将四个实体类插入数据库
     * @param postId
     * @param userId
     * @param content
     * @param imageKeys
     */

    public void createPost(Long postId, Long userId, String content, List<String> imageKeys) {
        OffsetDateTime now = OffsetDateTime.now();

        PostEntity post = new PostEntity();
        post.setPostId(postId);
        post.setUserId(userId);
        post.setContent(content);
        post.setStatus(1);
        post.setDeleted(0);
        post.setCreatedAt(now);
        post.setUpdatedAt(now);
        postMapper.insert(post);

        for (int i = 0; i < imageKeys.size(); i++) {
            PostImageEntity image = new PostImageEntity();
            image.setPostId(postId);
            image.setSortOrder(i);
            image.setImageKey(imageKeys.get(i));
            image.setCreatedAt(now);
            postImageMapper.insert(image);
        }

        PostStatEntity stat = new PostStatEntity();
        stat.setPostId(postId);
        stat.setLikeCount(0);
        stat.setCommentCount(0);
        stat.setUpdatedAt(now);
        postStatMapper.insert(stat);
    }
}
