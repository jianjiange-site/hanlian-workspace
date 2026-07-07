package com.aurora.dating.payment.manager;

import com.aurora.dating.payment.entity.PaymentCallbackRecordEntity;
import com.aurora.dating.payment.mapper.PaymentCallbackRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentCallbackRecordManager {

    private final PaymentCallbackRecordMapper paymentCallbackRecordMapper;

    public PaymentCallbackRecordManager(PaymentCallbackRecordMapper paymentCallbackRecordMapper) {
        this.paymentCallbackRecordMapper = paymentCallbackRecordMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentCallbackRecordEntity create(PaymentCallbackRecordEntity record) {
        paymentCallbackRecordMapper.insert(record);
        return record;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(Long id) {
        paymentCallbackRecordMapper.updateProcessResult(id, 20, "");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long id, String errorMessage) {
        paymentCallbackRecordMapper.updateProcessResult(id, 30, errorMessage);
    }

    public List<PaymentCallbackRecordEntity> listForDebug(String orderNo, String providerTradeNo, Integer limit) {
        LambdaQueryWrapper<PaymentCallbackRecordEntity> wrapper = new LambdaQueryWrapper<PaymentCallbackRecordEntity>()
                .eq(orderNo != null && !orderNo.isBlank(), PaymentCallbackRecordEntity::getOrderNo, orderNo)
                .eq(providerTradeNo != null && !providerTradeNo.isBlank(), PaymentCallbackRecordEntity::getProviderTradeNo, providerTradeNo)
                .orderByDesc(PaymentCallbackRecordEntity::getCreatedAt)
                .last("LIMIT " + limit);

        return paymentCallbackRecordMapper.selectList(wrapper);
    }
}
