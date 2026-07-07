package com.aurora.dating.match.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("match_pairs")
public class MatchPairEntity {

    @TableId(type = IdType.AUTO)
    private Long matchId;

    private Long userIdLow;

    private Long userIdHigh;

    private Integer source;

    private Integer status;

    private OffsetDateTime matchedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
