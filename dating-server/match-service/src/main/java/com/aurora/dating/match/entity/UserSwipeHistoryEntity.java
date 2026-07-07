package com.aurora.dating.match.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("user_swipe_history")
public class UserSwipeHistoryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long targetUserId;

    private Integer targetUserType;

    private Integer action;

    private Integer status;

    private Long matchId;

    private OffsetDateTime swipedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
