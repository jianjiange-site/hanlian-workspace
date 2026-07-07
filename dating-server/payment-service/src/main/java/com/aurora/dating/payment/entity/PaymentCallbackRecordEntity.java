package com.aurora.dating.payment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("payment_callback_records")
public class PaymentCallbackRecordEntity {

    private Long id;
    private String provider;
    private String eventType;
    private String orderNo;
    private String providerTradeNo;
    private Long paidAmountCents;
    private String currency;
    private String rawPayload;
    private Integer processStatus;
    private String errorMessage;
    private OffsetDateTime createdAt;
}
