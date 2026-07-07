package com.aurora.dating.match.mapper;

import com.aurora.dating.match.entity.VisitRecordEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface VisitRecordMapper extends BaseMapper<VisitRecordEntity> {

    @Update("""
            INSERT INTO visit_record (from_user_id, to_user_id, visit_count, status, visited_at, created_at, updated_at)
            VALUES (#{fromUserId}, #{toUserId}, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (from_user_id, to_user_id)
            DO UPDATE SET visit_count = visit_record.visit_count + 1,
                          visited_at = CURRENT_TIMESTAMP,
                          updated_at = CURRENT_TIMESTAMP
            """)
    int upsertVisit(@Param("fromUserId") Long fromUserId,
                    @Param("toUserId") Long toUserId);

    @Select("""
            SELECT *
            FROM visit_record
            WHERE to_user_id = #{toUserId}
              AND status = 1
            ORDER BY visited_at DESC
            LIMIT #{limit}
            """)
    List<VisitRecordEntity> listVisitsOfMe(@Param("toUserId") Long toUserId,
                                           @Param("limit") Integer limit);
}
