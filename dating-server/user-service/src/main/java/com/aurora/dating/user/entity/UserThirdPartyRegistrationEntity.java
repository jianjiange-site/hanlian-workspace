package com.aurora.dating.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("user_third_party_registration")
public class UserThirdPartyRegistrationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Integer platform;
    private String thirdPartyUserId;
    private String appName;
    private String email;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Boolean deleted;
}
