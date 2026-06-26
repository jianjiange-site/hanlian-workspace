package com.aurora.dating.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("user_info")
public class UserInfoEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String appName;
    private Boolean pending;
    private String nickname;
    private Integer gender;
    private Integer age;
    private LocalDate birthday;
    private String bio;
    private String preferredLocation;
    private String profession;
    private String education;
    private Integer height;
    private String customAvatar;
    private Integer regulationStatus;
    private OffsetDateTime lastOpenAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Boolean deleted;
}
