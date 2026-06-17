package com.jianjiange.dating.post.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jianjiange.dating.post.entity.PostEntity;
import com.jianjiange.dating.post.entity.PostImageEntity;
import com.jianjiange.dating.post.entity.PostStatEntity;
import com.jianjiange.dating.post.mapper.PostImageMapper;
import com.jianjiange.dating.post.mapper.PostMapper;
import com.jianjiange.dating.post.mapper.PostStatMapper;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;


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

    /**
     * 查看帖子
     * 条件：根据帖子ID搜索，帖子状态正常并且未被删除
     * @param postId
     * @return
     */
    public Optional<PostEntity> findNormalPostByPostId(Long postId) {
        LambdaQueryWrapper<PostEntity> wrapper = new LambdaQueryWrapper<PostEntity>()
                .eq(PostEntity::getPostId, postId)
                .eq(PostEntity::getStatus, 1)
                .eq(PostEntity::getDeleted, 0)
                .last("LIMIT 1");

        return Optional.ofNullable(postMapper.selectOne(wrapper));
    }

    /**
     * 根据帖子ID查询该帖子的iamges列表，并按SortOrder排序返回
     * @param postId
     * @return
     */
    public List<PostImageEntity> listImagesByPostId(Long postId) {
        LambdaQueryWrapper<PostImageEntity> wrapper = new LambdaQueryWrapper<PostImageEntity>()
                .eq(PostImageEntity::getPostId, postId)
                .orderByAsc(PostImageEntity::getSortOrder);

        return postImageMapper.selectList(wrapper);
    }

    /**
     * 查询点赞，评论...
     * @param postId
     * @return
     */
    public Optional<PostStatEntity> findStatByPostId(Long postId) {
        return Optional.ofNullable(postStatMapper.selectById(postId));
    }

    public int markPostDeleted(Long postId, Long userId) {
        LambdaUpdateWrapper<PostEntity> wrapper = new LambdaUpdateWrapper<PostEntity>()
                .eq(PostEntity::getPostId, postId)
                .eq(PostEntity::getUserId, userId)
                .eq(PostEntity::getDeleted, 0)
                .set(PostEntity::getStatus, 0)
                .set(PostEntity::getDeleted, 1)
                .set(PostEntity::getUpdatedAt, OffsetDateTime.now());

        return postMapper.update(null, wrapper);
    }
}
