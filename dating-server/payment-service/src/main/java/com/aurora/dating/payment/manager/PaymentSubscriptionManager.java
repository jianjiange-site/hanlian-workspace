package com.aurora.dating.payment.manager;

import com.aurora.dating.payment.entity.PaymentUserSubscriptionEntity;
import com.aurora.dating.payment.mapper.PaymentUserSubscriptionMapper;
import java.util.Optional;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;

@Component
public class PaymentSubscriptionManager {

    private final PaymentUserSubscriptionMapper subscriptionMapper;

    public PaymentSubscriptionManager(PaymentUserSubscriptionMapper subscriptionMapper) {
        this.subscriptionMapper = subscriptionMapper;
    }

    public Optional<PaymentUserSubscriptionEntity> findByUserId(Long userId) {
        return Optional.ofNullable(subscriptionMapper.selectById(userId));
    }

    public int upsertSubscription(Long userId, String tier, OffsetDateTime expireAt, Integer status) {
        return subscriptionMapper.upsertSubscription(userId, tier, expireAt, status, OffsetDateTime.now());
    }
}
