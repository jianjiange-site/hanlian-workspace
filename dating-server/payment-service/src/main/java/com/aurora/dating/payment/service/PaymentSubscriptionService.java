package com.aurora.dating.payment.service;

import com.aurora.dating.payment.entity.PaymentUserSubscriptionEntity;
import com.aurora.dating.payment.manager.PaymentSubscriptionManager;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;


@Service
public class PaymentSubscriptionService {

    private static final String TIER_FREE = "FREE";
    private static final String TIER_WEEKLY = "WEEKLY";
    private static final String TIER_MONTHLY = "MONTHLY";
    private static final String TIER_YEARLY = "YEARLY";

    private final PaymentSubscriptionManager paymentSubscriptionManager;

    public PaymentSubscriptionService(PaymentSubscriptionManager paymentSubscriptionManager) {
        this.paymentSubscriptionManager = paymentSubscriptionManager;
    }

    public SubscriptionResult getSubscription(Long userId) {
        validateUserId(userId);

        PaymentUserSubscriptionEntity subscription = paymentSubscriptionManager.findByUserId(userId)
                .orElse(null);

        if (subscription == null) {
            return freeResult(userId);
        }

        if (subscription.getStatus() == null || subscription.getStatus() != 1) {
            return freeResult(userId);
        }

        if (subscription.getExpireAt() == null || !subscription.getExpireAt().isAfter(OffsetDateTime.now())) {
            return freeResult(userId);
        }

        return resultByTier(userId, subscription.getTier(), subscription.getExpireAt());
    }

    public SubscriptionResult setSubscription(Long userId, String tier, Integer expireDays) {
        validateUserId(userId);
        validateTier(tier);

        String normalizedTier = tier.toUpperCase();

        if (TIER_FREE.equals(normalizedTier)) {
            paymentSubscriptionManager.upsertSubscription(userId, TIER_FREE, null, 0);
            return freeResult(userId);
        }

        if (expireDays == null || expireDays <= 0) {
            throw new IllegalArgumentException("expire_days must be positive");
        }

        OffsetDateTime expireAt = OffsetDateTime.now().plusDays(expireDays);
        paymentSubscriptionManager.upsertSubscription(userId, normalizedTier, expireAt, 1);

        return resultByTier(userId, normalizedTier, expireAt);
    }

    private void validateTier(String tier) {
        if (tier == null || tier.isBlank()) {
            throw new IllegalArgumentException("tier must not be blank");
        }

        if (!TIER_FREE.equalsIgnoreCase(tier)
                && !TIER_WEEKLY.equalsIgnoreCase(tier)
                && !TIER_MONTHLY.equalsIgnoreCase(tier)
                && !TIER_YEARLY.equalsIgnoreCase(tier)) {
            throw new IllegalArgumentException("tier must be FREE, WEEKLY, MONTHLY or YEARLY");
        }
    }

    private SubscriptionResult resultByTier(Long userId, String tier, OffsetDateTime expireAt) {
        if (TIER_WEEKLY.equalsIgnoreCase(tier)) {
            return activeResult(userId, TIER_WEEKLY, expireAt, 40, 80, 0);
        }

        if (TIER_MONTHLY.equalsIgnoreCase(tier)) {
            return activeResult(userId, TIER_MONTHLY, expireAt, 80, 120, 1);
        }

        if (TIER_YEARLY.equalsIgnoreCase(tier)) {
            return activeResult(userId, TIER_YEARLY, expireAt, 80, 120, 1);
        }

        return freeResult(userId);
    }

    private SubscriptionResult freeResult(Long userId) {
        return new SubscriptionResult(
                PaymentCoinService.CODE_OK,
                "ok",
                userId,
                TIER_FREE,
                false,
                0L,
                20,
                50,
                0
        );
    }

    private SubscriptionResult activeResult(Long userId,
                                            String tier,
                                            OffsetDateTime expireAt,
                                            Integer dailyRightSwipeLimit,
                                            Integer dailyCardLimit,
                                            Integer dailySuperHiLimit) {
        return new SubscriptionResult(
                PaymentCoinService.CODE_OK,
                "ok",
                userId,
                tier,
                true,
                expireAt.toInstant().toEpochMilli(),
                dailyRightSwipeLimit,
                dailyCardLimit,
                dailySuperHiLimit
        );
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("user_id must be positive");
        }
    }
}
