package com.aurora.dating.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("user_interest")
public class UserInterestEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String interestCode;

    private String displayName;

    private Integer type;

    private String picKey;

    private Integer sortOrder;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
