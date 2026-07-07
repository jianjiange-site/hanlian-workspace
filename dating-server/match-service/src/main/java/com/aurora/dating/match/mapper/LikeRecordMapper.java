package com.aurora.dating.match.mapper;

import com.aurora.dating.match.entity.LikeRecordEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LikeRecordMapper extends BaseMapper<LikeRecordEntity> {

    @Select("""
            SELECT *
            FROM like_record
            WHERE from_user_id = #{fromUserId}
              AND to_user_id = #{toUserId}
            LIMIT 1
            """)
    LikeRecordEntity findByFromAndTo(@Param("fromUserId") Long fromUserId,
                                     @Param("toUserId") Long toUserId);

    @Select("""
            SELECT *
            FROM like_record
            WHERE to_user_id = #{toUserId}
              AND status = 1
            ORDER BY liked_at DESC
            LIMIT #{limit}
            """)
    List<LikeRecordEntity> listLikesOfMe(@Param("toUserId") Long toUserId,
                                         @Param("limit") Integer limit);
}
