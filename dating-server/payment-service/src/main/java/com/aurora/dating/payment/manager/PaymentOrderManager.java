package com.aurora.dating.payment.manager;

import com.aurora.dating.payment.entity.PaymentOrderEntity;
import com.aurora.dating.payment.mapper.PaymentOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class PaymentOrderManager {

    private final PaymentOrderMapper paymentOrderMapper;

    public PaymentOrderManager(PaymentOrderMapper paymentOrderMapper) {
        this.paymentOrderMapper = paymentOrderMapper;
    }

    public void createOrder(PaymentOrderEntity order) {
        paymentOrderMapper.insert(order);
    }

    public Optional<PaymentOrderEntity> findByOrderNo(String orderNo) {
        LambdaQueryWrapper<PaymentOrderEntity> wrapper = new LambdaQueryWrapper<PaymentOrderEntity>()
                .eq(PaymentOrderEntity::getOrderNo, orderNo)
                .last("LIMIT 1");

        return Optional.ofNullable(paymentOrderMapper.selectOne(wrapper));
    }

    public int markPaidIfCreated(String orderNo, String providerTradeNo, OffsetDateTime paidAt) {
        return paymentOrderMapper.markPaidIfCreated(orderNo, providerTradeNo, paidAt);
    }

    public int markClosedIfCreated(String orderNo, OffsetDateTime closedAt) {
        return paymentOrderMapper.markClosedIfCreated(orderNo, closedAt);
    }

    public List<PaymentOrderEntity> listByUserId(Long userId, Integer limit) {
        LambdaQueryWrapper<PaymentOrderEntity> wrapper = new LambdaQueryWrapper<PaymentOrderEntity>()
                .eq(PaymentOrderEntity::getUserId, userId)
                .orderByDesc(PaymentOrderEntity::getCreatedAt)
                .last("LIMIT " + limit);

        return paymentOrderMapper.selectList(wrapper);
    }

    public Optional<PaymentOrderEntity> findByProviderTradeNo(String providerTradeNo) {
        LambdaQueryWrapper<PaymentOrderEntity> wrapper = new LambdaQueryWrapper<PaymentOrderEntity>()
                .eq(PaymentOrderEntity::getProviderTradeNo, providerTradeNo)
                .last("LIMIT 1");

        return Optional.ofNullable(paymentOrderMapper.selectOne(wrapper));
    }

    public int closeExpiredCreatedOrders(OffsetDateTime expiredBefore, OffsetDateTime closedAt) {
        return paymentOrderMapper.closeExpiredCreatedOrders(expiredBefore, closedAt);
    }
}
