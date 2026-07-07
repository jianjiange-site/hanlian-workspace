package com.aurora.dating.payment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("payment_coin_ledger")
public class PaymentCoinLedgerEntity {

    private Long id;

    private String ledgerNo;

    private Long userId;

    private Long changeAmount;

    private Long balanceAfter;

    private Integer direction;

    private String reason;

    private String idempotencyKey;

    private String remark;

    private OffsetDateTime createdAt;
}
