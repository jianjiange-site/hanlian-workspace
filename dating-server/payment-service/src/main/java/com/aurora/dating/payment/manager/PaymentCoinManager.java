package com.aurora.dating.payment.manager;

import com.aurora.dating.payment.entity.PaymentCoinAccountEntity;
import com.aurora.dating.payment.entity.PaymentCoinLedgerEntity;
import com.aurora.dating.payment.mapper.PaymentCoinAccountMapper;
import com.aurora.dating.payment.mapper.PaymentCoinLedgerMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PaymentCoinManager {

    private final PaymentCoinAccountMapper accountMapper;
    private final PaymentCoinLedgerMapper ledgerMapper;

    public PaymentCoinManager(PaymentCoinAccountMapper accountMapper,
                              PaymentCoinLedgerMapper ledgerMapper) {
        this.accountMapper = accountMapper;
        this.ledgerMapper = ledgerMapper;
    }

    public Optional<PaymentCoinAccountEntity> findAccount(Long userId) {
        return Optional.ofNullable(accountMapper.selectById(userId));
    }

    public PaymentCoinAccountEntity createAccountIfAbsent(Long userId) {
        PaymentCoinAccountEntity existing = accountMapper.selectById(userId);
        if (existing != null) {
            return existing;
        }

        OffsetDateTime now = OffsetDateTime.now();

        PaymentCoinAccountEntity account = new PaymentCoinAccountEntity();
        account.setUserId(userId);
        account.setBalance(0L);
        account.setTotalRecharge(0L);
        account.setTotalConsume(0L);
        account.setStatus(1);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);

        accountMapper.insert(account);
        return account;
    }

    public Optional<PaymentCoinLedgerEntity> findLedgerByIdempotencyKey(String idempotencyKey) {
        LambdaQueryWrapper<PaymentCoinLedgerEntity> wrapper = new LambdaQueryWrapper<PaymentCoinLedgerEntity>()
                .eq(PaymentCoinLedgerEntity::getIdempotencyKey, idempotencyKey)
                .last("LIMIT 1");

        return Optional.ofNullable(ledgerMapper.selectOne(wrapper));
    }

    public int consumeCoins(Long userId, Long amount) {
        return accountMapper.consumeCoins(userId, amount);
    }

    public int grantCoins(Long userId, Long amount) {
        return accountMapper.grantCoins(userId, amount);
    }

    public void createLedger(PaymentCoinLedgerEntity ledger) {
        ledgerMapper.insert(ledger);
    }
}
