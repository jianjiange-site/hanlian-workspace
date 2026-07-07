package com.aurora.dating.payment.mapper;

import com.aurora.dating.payment.entity.PaymentOrderEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrderEntity> {

    @Update("""
            UPDATE payment_orders
            SET status = 20,
                provider_trade_no = #{providerTradeNo},
                paid_at = #{paidAt},
                updated_at = #{paidAt}
            WHERE order_no = #{orderNo}
              AND status = 10
            """)
    int markPaidIfCreated(@Param("orderNo") String orderNo,
                          @Param("providerTradeNo") String providerTradeNo,
                          @Param("paidAt") OffsetDateTime paidAt);

    @Update("""
        UPDATE payment_orders
        SET status = 30,
            closed_at = #{closedAt},
            updated_at = #{closedAt}
        WHERE order_no = #{orderNo}
          AND status = 10
        """)
    int markClosedIfCreated(@Param("orderNo") String orderNo,
                            @Param("closedAt") OffsetDateTime closedAt);

    @Update("""
        UPDATE payment_orders
        SET status = 30,
            closed_at = #{closedAt},
            updated_at = #{closedAt}
        WHERE status = 10
          AND created_at <= #{expiredBefore}
        """)
    int closeExpiredCreatedOrders(@Param("expiredBefore") OffsetDateTime expiredBefore,
                                  @Param("closedAt") OffsetDateTime closedAt);
}
