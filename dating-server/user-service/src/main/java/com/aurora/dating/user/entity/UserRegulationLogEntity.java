package com.aurora.dating.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("user_regulation_log")
public class UserRegulationLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Integer beforeStatus;

    private Integer afterStatus;

    private String reason;

    private Integer operatorType;

    private String operatorId;

    private OffsetDateTime createdAt;
}
