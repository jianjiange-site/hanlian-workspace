package com.aurora.dating.payment.service;

import lombok.Getter;

@Getter
public class ConsumeCoinsResult {

    private final int code;
    private final String message;
    private final Long balance;
    private final String ledgerNo;

    private ConsumeCoinsResult(int code, String message, Long balance, String ledgerNo) {
        this.code = code;
        this.message = message;
        this.balance = balance;
        this.ledgerNo = ledgerNo;
    }

    public static ConsumeCoinsResult success(Long balance, String ledgerNo) {
        return new ConsumeCoinsResult(PaymentCoinService.CODE_OK, "ok", balance, ledgerNo);
    }

    public static ConsumeCoinsResult insufficient(Long balance) {
        return new ConsumeCoinsResult(PaymentCoinService.CODE_INSUFFICIENT_COINS, "insufficient coins", balance, "");
    }
}
