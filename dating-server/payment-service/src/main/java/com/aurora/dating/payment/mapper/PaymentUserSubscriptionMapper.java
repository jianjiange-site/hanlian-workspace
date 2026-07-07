package com.aurora.dating.payment.mapper;

import com.aurora.dating.payment.entity.PaymentUserSubscriptionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


@Mapper
public interface PaymentUserSubscriptionMapper extends BaseMapper<PaymentUserSubscriptionEntity> {

    @Insert("""
            INSERT INTO payment_user_subscriptions (user_id, tier, expire_at, status, created_at, updated_at)
            VALUES (#{userId}, #{tier}, #{expireAt}, #{status}, #{now}, #{now})
            ON CONFLICT (user_id)
            DO UPDATE SET tier = EXCLUDED.tier,
                          expire_at = EXCLUDED.expire_at,
                          status = EXCLUDED.status,
                          updated_at = EXCLUDED.updated_at
            """)
    int upsertSubscription(@Param("userId") Long userId,
                           @Param("tier") String tier,
                           @Param("expireAt") OffsetDateTime expireAt,
                           @Param("status") Integer status,
                           @Param("now") OffsetDateTime now);
}
