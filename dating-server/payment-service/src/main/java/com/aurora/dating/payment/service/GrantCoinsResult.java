package com.aurora.dating.payment.service;

import lombok.Getter;

@Getter
public class GrantCoinsResult {

    private final int code;
    private final String message;
    private final Long balance;
    private final String ledgerNo;

    private GrantCoinsResult(int code, String message, Long balance, String ledgerNo) {
        this.code = code;
        this.message = message;
        this.balance = balance;
        this.ledgerNo = ledgerNo;
    }

    public static GrantCoinsResult success(Long balance, String ledgerNo) {
        return new GrantCoinsResult(PaymentCoinService.CODE_OK, "ok", balance, ledgerNo);
    }
}
