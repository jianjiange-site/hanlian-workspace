package com.aurora.dating.payment.mapper;

import com.aurora.dating.payment.entity.PaymentCoinAccountEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PaymentCoinAccountMapper extends BaseMapper<PaymentCoinAccountEntity> {

    @Update("""
            UPDATE payment_coin_accounts
            SET balance = balance - #{amount},
                total_consume = total_consume + #{amount},
                updated_at = CURRENT_TIMESTAMP
            WHERE user_id = #{userId}
              AND status = 1
              AND balance >= #{amount}
            """)
    int consumeCoins(@Param("userId") Long userId, @Param("amount") Long amount);

    @Update("""
            UPDATE payment_coin_accounts
            SET balance = balance + #{amount},
                total_recharge = total_recharge + #{amount},
                updated_at = CURRENT_TIMESTAMP
            WHERE user_id = #{userId}
              AND status = 1
            """)
    int grantCoins(@Param("userId") Long userId, @Param("amount") Long amount);
}
