package com.aurora.dating.payment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("payment_orders")
public class PaymentOrderEntity {

    private Long id;

    /**
     * 业务订单号
     */
    private String orderNo;

    private Long userId;

    /**
     * 商品类型
     * 目前两类：COIN（金币包），SUBSCRIPTION（订阅会员）
     */
    private String productType;

    /**
     * 商品编码
     */
    private String productCode;

    /**
     * 支付渠道
     */
    private String provider;

    /**
     * 订单金额，单位为分
     */
    private Long amountCents;

    /**
     * 币种
     */
    private String currency;

    /**
     * 订单对应金币
     */
    private Long coinAmount;

    /**
     * 订阅档位
     */
    private String subscriptionTier;

    /**
     * 订阅状态
     * 10 = CREATED，订单已创建，等待支付
     * 20 = PAID，订单已支付
     * 30 = CLOSED，订单已关闭
     */
    private Integer status;

    /**
     * 第三方支付平台的交易号
     */
    private String providerTradeNo;

    private OffsetDateTime paidAt;

    private OffsetDateTime closedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
