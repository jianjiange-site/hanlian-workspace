package com.aurora.dating.match.mapper;

import com.aurora.dating.match.entity.UserSwipeHistoryEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserSwipeHistoryMapper extends BaseMapper<UserSwipeHistoryEntity> {

    @Select("""
            SELECT *
            FROM user_swipe_history
            WHERE user_id = #{userId}
              AND target_user_id = #{targetUserId}
            LIMIT 1
            """)
    UserSwipeHistoryEntity findByUserAndTarget(@Param("userId") Long userId,
                                               @Param("targetUserId") Long targetUserId);
}
