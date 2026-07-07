package com.aurora.dating.payment.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("payment_coin_accounts")
public class PaymentCoinAccountEntity {

    @TableId
    private Long userId;

    private Long balance;

    private Long totalRecharge;

    private Long totalConsume;

    private Integer status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
