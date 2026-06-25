package com.aurora.dating.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aurora.dating.post.entity.PostCommentEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PostCommentMapper extends BaseMapper<PostCommentEntity> {
}