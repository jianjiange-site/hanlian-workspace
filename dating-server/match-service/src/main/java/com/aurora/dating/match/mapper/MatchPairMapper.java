package com.aurora.dating.match.mapper;

import com.aurora.dating.match.entity.MatchPairEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MatchPairMapper extends BaseMapper<MatchPairEntity> {

    @Select("""
            SELECT *
            FROM match_pairs
            WHERE user_id_low = #{userIdLow}
              AND user_id_high = #{userIdHigh}
            LIMIT 1
            """)
    MatchPairEntity findByUserPair(@Param("userIdLow") Long userIdLow,
                                   @Param("userIdHigh") Long userIdHigh);
}
