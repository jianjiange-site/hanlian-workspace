package com.aurora.dating.payment.service;

public class CloseOrderResult {

    private final int code;
    private final String message;
    private final String orderNo;
    private final Long userId;
    private final String productType;
    private final String productCode;
    private final Integer status;
    private final Long closedAtMs;

    public CloseOrderResult(int code, String message, String orderNo, Long userId,
                            String productType, String productCode, Integer status, Long closedAtMs) {
        this.code = code;
        this.message = message;
        this.orderNo = orderNo;
        this.userId = userId;
        this.productType = productType;
        this.productCode = productCode;
        this.status = status;
        this.closedAtMs = closedAtMs;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
    public String getOrderNo() { return orderNo; }
    public Long getUserId() { return userId; }
    public String getProductType() { return productType; }
    public String getProductCode() { return productCode; }
    public Integer getStatus() { return status; }
    public Long getClosedAtMs() { return closedAtMs; }
}
