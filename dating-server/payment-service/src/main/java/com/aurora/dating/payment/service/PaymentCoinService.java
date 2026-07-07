package com.aurora.dating.payment.service;

import com.aurora.dating.payment.error.PaymentErrorCode;
import com.aurora.dating.payment.entity.PaymentCoinAccountEntity;
import com.aurora.dating.payment.entity.PaymentCoinLedgerEntity;
import com.aurora.dating.payment.manager.PaymentCoinManager;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentCoinService {

    public static final int CODE_OK = PaymentErrorCode.OK;
    public static final int CODE_INSUFFICIENT_COINS = PaymentErrorCode.INSUFFICIENT_COINS;
    public static final int CODE_BAD_REQUEST = PaymentErrorCode.BAD_REQUEST;

    private static final int DIRECTION_OUT = 2;

    private static final int DIRECTION_IN = 1;

    private final PaymentCoinManager paymentCoinManager;

    public PaymentCoinService(PaymentCoinManager paymentCoinManager) {
        this.paymentCoinManager = paymentCoinManager;
    }

    /**
     * 查询用户金额
     * @param userId
     * @return
     */
    public PaymentCoinAccountEntity getCoins(Long userId) {
        validateUserId(userId);
        return paymentCoinManager.createAccountIfAbsent(userId);
    }

    /**
     * 扣金币
     * @param userId
     * @param amount
     * @param reason
     * @param idempotencyKey 防重复扣费的业务唯一键。
     * @return
     */
    @Transactional
    public ConsumeCoinsResult consumeCoins(Long userId, Long amount, String reason, String idempotencyKey) {
        validateConsumeRequest(userId, amount, reason, idempotencyKey);

        PaymentCoinAccountEntity account = paymentCoinManager.createAccountIfAbsent(userId);

        PaymentCoinLedgerEntity existingLedger = paymentCoinManager.findLedgerByIdempotencyKey(idempotencyKey)
                .orElse(null);
        if (existingLedger != null) {
            return ConsumeCoinsResult.success(existingLedger.getBalanceAfter(), existingLedger.getLedgerNo());
        }

        int updated = paymentCoinManager.consumeCoins(userId, amount);
        if (updated == 0) {
            return ConsumeCoinsResult.insufficient(account.getBalance());
        }

        PaymentCoinAccountEntity latestAccount = paymentCoinManager.findAccount(userId)
                .orElseThrow(() -> new IllegalStateException("payment coin account missing after consume"));

        String ledgerNo = UUID.randomUUID().toString().replace("-", "");

        PaymentCoinLedgerEntity ledger = new PaymentCoinLedgerEntity();
        ledger.setLedgerNo(ledgerNo);
        ledger.setUserId(userId);
        ledger.setChangeAmount(-amount);
        ledger.setBalanceAfter(latestAccount.getBalance());
        ledger.setDirection(DIRECTION_OUT);
        ledger.setReason(reason);
        ledger.setIdempotencyKey(idempotencyKey);
        ledger.setRemark(null);
        ledger.setCreatedAt(OffsetDateTime.now());

        paymentCoinManager.createLedger(ledger);

        return ConsumeCoinsResult.success(latestAccount.getBalance(), ledgerNo);
    }


    /**
     * 加金币
     * @param userId
     * @param amount
     * @param reason
     * @param idempotencyKey
     * @return
     */
    @Transactional
    public GrantCoinsResult grantCoins(Long userId, Long amount, String reason, String idempotencyKey) {
        validateConsumeRequest(userId, amount, reason, idempotencyKey);

        paymentCoinManager.createAccountIfAbsent(userId);

        PaymentCoinLedgerEntity existingLedger = paymentCoinManager.findLedgerByIdempotencyKey(idempotencyKey)
                .orElse(null);
        if (existingLedger != null) {
            return GrantCoinsResult.success(existingLedger.getBalanceAfter(), existingLedger.getLedgerNo());
        }

        int updated = paymentCoinManager.grantCoins(userId, amount);
        if (updated == 0) {
            throw new IllegalStateException("payment coin account unavailable");
        }

        PaymentCoinAccountEntity latestAccount = paymentCoinManager.findAccount(userId)
                .orElseThrow(() -> new IllegalStateException("payment coin account missing after grant"));

        String ledgerNo = UUID.randomUUID().toString().replace("-", "");

        PaymentCoinLedgerEntity ledger = new PaymentCoinLedgerEntity();
        ledger.setLedgerNo(ledgerNo);
        ledger.setUserId(userId);
        ledger.setChangeAmount(amount);
        ledger.setBalanceAfter(latestAccount.getBalance());
        ledger.setDirection(DIRECTION_IN);
        ledger.setReason(reason);
        ledger.setIdempotencyKey(idempotencyKey);
        ledger.setRemark(null);
        ledger.setCreatedAt(OffsetDateTime.now());

        paymentCoinManager.createLedger(ledger);

        return GrantCoinsResult.success(latestAccount.getBalance(), ledgerNo);
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("user_id must be positive");
        }
    }

    private void validateConsumeRequest(Long userId, Long amount, String reason, String idempotencyKey) {
        validateUserId(userId);

        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotency_key must not be blank");
        }
    }
}
