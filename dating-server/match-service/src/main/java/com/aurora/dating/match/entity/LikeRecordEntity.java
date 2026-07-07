package com.aurora.dating.match.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("like_record")
public class LikeRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long fromUserId;

    private Long toUserId;

    private Integer source;

    private Integer status;

    private OffsetDateTime likedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
