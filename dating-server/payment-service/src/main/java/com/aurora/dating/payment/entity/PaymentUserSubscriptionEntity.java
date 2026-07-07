package com.aurora.dating.payment.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("payment_user_subscriptions")
public class PaymentUserSubscriptionEntity {

    @TableId
    private Long userId;

    private String tier;

    private OffsetDateTime expireAt;

    private Integer status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}