package com.aurora.dating.payment.mapper;

import com.aurora.dating.payment.entity.PaymentCallbackRecordEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PaymentCallbackRecordMapper extends BaseMapper<PaymentCallbackRecordEntity> {

    @Update("""
            UPDATE payment_callback_records
            SET process_status = #{processStatus},
                error_message = #{errorMessage}
            WHERE id = #{id}
            """)
    int updateProcessResult(@Param("id") Long id,
                            @Param("processStatus") Integer processStatus,
                            @Param("errorMessage") String errorMessage);
}
