package com.aurora.dating.post.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.aurora.dating.post.entity.*;
import com.aurora.dating.post.mapper.*;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;


@Component
public class PostManager {

    private final PostMapper postMapper;
    private final PostImageMapper postImageMapper;
    private final PostStatMapper postStatMapper;
    private final PostLikeMapper postLikeMapper;
    private final PostCommentMapper postCommentMapper;

    public PostManager(PostMapper postMapper,
                       PostImageMapper postImageMapper,
                       PostStatMapper postStatMapper,
                       PostLikeMapper postLikeMapper,
                       PostCommentMapper postCommentMapper) {
        this.postMapper = postMapper;
        this.postImageMapper = postImageMapper;
        this.postStatMapper = postStatMapper;
        this.postLikeMapper = postLikeMapper;
        this.postCommentMapper = postCommentMapper;
    }

    /**
     * 新建一个帖子
     *
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
     * 根据postId查看一个帖子
     *
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
     * 查看一个帖子里面的图片信息
     *
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
     * 查询总计表
     *
     * 查询点赞，评论...
     * @param postId
     * @return
     */
    public Optional<PostStatEntity> findStatByPostId(Long postId) {
        return Optional.ofNullable(postStatMapper.selectById(postId));
    }


    /**
     * 删除一个帖子
     *
     * 逻辑删除，实际上是updata修改status帖子状态
     * 返回是0或1（影响的数据库数据条数）
     * @param postId
     * @param userId
     * @return
     */
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

    /**
     * 查看帖子状态是否正常
     *
     * @param postId
     * @return
     */
    public boolean existsNormalPost(Long postId) {
        return findNormalPostByPostId(postId).isPresent();
    }

    /**
     * 根据用户id和帖子id判断用户是否点赞
     *
     * @param userId
     * @param postId
     * @return
     */
    public Optional<PostLikeEntity> findLikeByUserIdAndPostId(Long userId, Long postId) {
        LambdaQueryWrapper<PostLikeEntity> wrapper = new LambdaQueryWrapper<PostLikeEntity>()
                .eq(PostLikeEntity::getUserId, userId)
                .eq(PostLikeEntity::getPostId, postId)
                .last("LIMIT 1");

        return Optional.ofNullable(postLikeMapper.selectOne(wrapper));
    }

    public void upsertLike(Long userId, Long postId, int status) {
        postLikeMapper.upsertLike(userId, postId, status, OffsetDateTime.now());
    }

    public int updateLikeStatus(Long userId, Long postId, int status) {
        LambdaUpdateWrapper<PostLikeEntity> wrapper = new LambdaUpdateWrapper<PostLikeEntity>()
                .eq(PostLikeEntity::getUserId, userId)
                .eq(PostLikeEntity::getPostId, postId)
                .set(PostLikeEntity::getStatus, status)
                .set(PostLikeEntity::getUpdatedAt, OffsetDateTime.now());

        return postLikeMapper.update(null, wrapper);
    }

    public int increaseLikeCount(Long postId, int delta) {
        LambdaUpdateWrapper<PostStatEntity> wrapper = new LambdaUpdateWrapper<PostStatEntity>()
                .eq(PostStatEntity::getPostId, postId)
                .setSql("like_count = GREATEST(like_count + (" + delta + "), 0)")
                .set(PostStatEntity::getUpdatedAt, OffsetDateTime.now());

        return postStatMapper.update(null, wrapper);
    }

    /**
     * 新建评论
     * 将评论实体插入数据库
     * @param comment
     */
    public void createComment(PostCommentEntity comment){
        postCommentMapper.insert(comment);
    }

    /**
     * 分页查询一级评论
     * @param postId
     * @param cursorCommentId 当前评论limit里面最后一条的游标
     * @param limit
     * @return
     */
    public List<PostCommentEntity> listRootCommentsByPostId(Long postId, Long cursorCommentId, int limit) {
        LambdaQueryWrapper<PostCommentEntity> wrapper = new LambdaQueryWrapper<PostCommentEntity>()
                .eq(PostCommentEntity::getPostId, postId)
                .eq(PostCommentEntity::getRootId, 0L)
                .eq(PostCommentEntity::getDeleted, 0)
                .eq(PostCommentEntity::getStatus, 1)
                .orderByDesc(PostCommentEntity::getCommentId)
                .last("LIMIT " + limit);

        if (cursorCommentId != null && cursorCommentId > 0) {
            wrapper.lt(PostCommentEntity::getCommentId, cursorCommentId);
        }

        return postCommentMapper.selectList(wrapper);
    }

    /**
     * 更新评论数量
     * GREATSET保证评论数量不会小于0
     * @param postId
     * @param delta
     * @return
     */
    public int increaseCommentCount(Long postId, int delta){
        LambdaUpdateWrapper<PostStatEntity> wrapper = new LambdaUpdateWrapper<PostStatEntity>()
                .eq(PostStatEntity::getPostId, postId)
                .setSql("comment_count = GREATEST(comment_count + (" + delta + "), 0)")
                .set(PostStatEntity::getUpdatedAt, OffsetDateTime.now());

        return postStatMapper.update(null, wrapper);
    }

    /**
     * 查询多条帖子
     *
     * 根据postId来查（最新的帖子在最前面）
     * @param userId
     * @param cursorPostId
     * @param limit
     * @return
     */
    public List<PostEntity> listNormalPostsByUserId(Long userId, Long cursorPostId, int limit) {
        LambdaQueryWrapper<PostEntity> wrapper = new LambdaQueryWrapper<PostEntity>()
                .eq(PostEntity::getUserId, userId)
                .eq(PostEntity::getStatus, 1)
                .eq(PostEntity::getDeleted, 0)
                .orderByDesc(PostEntity::getPostId)
                .last("LIMIT " + limit);

        if (cursorPostId != null && cursorPostId > 0) {
            wrapper.lt(PostEntity::getPostId, cursorPostId);
        }

        return postMapper.selectList(wrapper);
    }

    /**
     * 查询帖子列表
     *
     * 查询{since}天内的limit条数据
     * @param since
     * @param limit
     * @return
     */
    public List<PostEntity> listRecentNormalPosts(OffsetDateTime since, int limit) {
        LambdaQueryWrapper<PostEntity> wrapper = new LambdaQueryWrapper<PostEntity>()
                .eq(PostEntity::getStatus, 1)
                .eq(PostEntity::getDeleted, 0)
                .ge(PostEntity::getCreatedAt, since)
                .orderByDesc(PostEntity::getPostId)
                .last("LIMIT " + limit);

        return postMapper.selectList(wrapper);
    }
}
