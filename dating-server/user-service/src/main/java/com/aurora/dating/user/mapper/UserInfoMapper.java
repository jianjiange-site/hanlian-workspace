package com.aurora.dating.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aurora.dating.user.entity.UserInfoEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfoEntity> {

    @Update("""
            UPDATE user_info
            SET custom_avatar = CAST(#{customAvatar} AS jsonb),
                updated_at = NOW()
            WHERE user_id = #{userId}
              AND deleted = FALSE
            """)
    int updateCustomAvatar(
            @Param("userId") Long userId,
            @Param("customAvatar") String customAvatar);
}
