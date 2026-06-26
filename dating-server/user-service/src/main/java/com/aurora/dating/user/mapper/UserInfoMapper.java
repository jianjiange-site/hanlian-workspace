package com.aurora.dating.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aurora.dating.user.entity.UserInfoEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfoEntity> {
}