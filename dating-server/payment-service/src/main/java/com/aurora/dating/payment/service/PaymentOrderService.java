package com.aurora.dating.payment.service;

import com.aurora.dating.payment.entity.PaymentOrderEntity;
import com.aurora.dating.payment.manager.PaymentOrderManager;
import com.aurora.dating.payment.provider.PaymentCallbackCommand;
import com.aurora.dating.payment.provider.PaymentCallbackProviderResult;
import com.aurora.dating.payment.provider.PaymentProviderRouter;
import com.aurora.dating.payment.provider.PreparePaymentCommand;
import com.aurora.dating.payment.provider.PreparePaymentProviderResult;
import com.aurora.dating.payment.success.PaymentSuccessHandlerRouter;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import com.aurora.dating.payment.entity.PaymentCallbackRecordEntity;
import com.aurora.dating.payment.manager.PaymentCallbackRecordManager;


@Service
public class PaymentOrderService {

    private static final int STATUS_CREATED = 10;
    private static final int STATUS_PAID = 20;
    private static final String CURRENCY_USD = "USD";
    private static final int STATUS_CLOSED = 30;
    private static final int CALLBACK_STATUS_PROCESSING = 10;

    private final PaymentOrderManager paymentOrderManager;
    private final PaymentCallbackRecordManager paymentCallbackRecordManager;
    private final PaymentProviderRouter paymentProviderRouter;
    private final PaymentSuccessHandlerRouter paymentSuccessHandlerRouter;

    public PaymentOrderService(PaymentOrderManager paymentOrderManager,
                               PaymentCallbackRecordManager paymentCallbackRecordManager,
                               PaymentProviderRouter paymentProviderRouter,
                               PaymentSuccessHandlerRouter paymentSuccessHandlerRouter) {
        this.paymentOrderManager = paymentOrderManager;
        this.paymentCallbackRecordManager = paymentCallbackRecordManager;
        this.paymentProviderRouter = paymentProviderRouter;
        this.paymentSuccessHandlerRouter = paymentSuccessHandlerRouter;
    }

    /**
     * 创建订单
     * @param userId
     * @param productCode
     * @param provider
     * @return
     */
    @Transactional
    public CreateOrderResult createOrder(Long userId, String productCode, String provider) {
        validateUserId(userId);
        validateProvider(provider);

        ProductSpec product = resolveProduct(productCode);
        String orderNo = generateOrderNo();
        OffsetDateTime now = OffsetDateTime.now();

        PaymentOrderEntity order = new PaymentOrderEntity();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setProductType(product.productType());
        order.setProductCode(product.productCode());
        order.setProvider(provider.toUpperCase());
        order.setAmountCents(product.amountCents());
        order.setCurrency(CURRENCY_USD);
        order.setCoinAmount(product.coinAmount());
        order.setSubscriptionTier(product.subscriptionTier());
        order.setStatus(STATUS_CREATED);
        order.setProviderTradeNo(null);
        order.setPaidAt(null);
        order.setClosedAt(null);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        paymentOrderManager.createOrder(order);

        return new CreateOrderResult(
                PaymentCoinService.CODE_OK,
                "ok",
                orderNo,
                userId,
                product.productType(),
                product.productCode(),
                provider.toUpperCase(),
                product.amountCents(),
                CURRENCY_USD,
                product.coinAmount(),
                product.subscriptionTier(),
                STATUS_CREATED
        );
    }

    @Transactional
    public PaySuccessResult mockPaySuccess(String orderNo,
                                           String providerTradeNo,
                                           Long paidAmountCents,
                                           String currency) {
        validateOrderNo(orderNo);
        validateProviderTradeNo(providerTradeNo);
        validatePaidAmountCents(paidAmountCents);
        validateCurrency(currency);

        PaymentCallbackProviderResult callbackResult = paymentProviderRouter
                .getClient("MOCK")
                .parsePaymentCallback(new PaymentCallbackCommand(
                        "MOCK",
                        "MOCK_PAY_SUCCESS",
                        orderNo,
                        providerTradeNo,
                        paidAmountCents,
                        currency,
                        mockPaySuccessRawPayload(orderNo, providerTradeNo, paidAmountCents, currency)
                ));

        PaymentCallbackRecordEntity callbackRecord = createMockPaySuccessCallbackRecord(
                callbackResult
        );

        try {
            PaymentOrderEntity order = paymentOrderManager.findByOrderNo(callbackResult.getOrderNo())
                    .orElseThrow(() -> new IllegalArgumentException("order not found"));

            validatePaidOrderAmount(order, callbackResult.getPaidAmountCents(), callbackResult.getCurrency());
            validateProviderTradeNoNotUsed(callbackResult.getOrderNo(), callbackResult.getProviderTradeNo());

            PaySuccessResult result;
            if (order.getStatus() != null && order.getStatus() == STATUS_PAID) {
                validateAlreadyPaidProviderTradeNo(order, callbackResult.getProviderTradeNo());
                result = paymentSuccessHandlerRouter.getHandler(order.getProductType()).alreadyPaid(order);
            } else {
                if (order.getStatus() == null || order.getStatus() != STATUS_CREATED) {
                    throw new IllegalArgumentException("order status is not CREATED");
                }

                OffsetDateTime paidAt = OffsetDateTime.now();
                int updated = paymentOrderManager.markPaidIfCreated(callbackResult.getOrderNo(), callbackResult.getProviderTradeNo(), paidAt);
                if (updated == 0) {
                    PaymentOrderEntity latestOrder = paymentOrderManager.findByOrderNo(callbackResult.getOrderNo())
                            .orElseThrow(() -> new IllegalArgumentException("order not found"));
                    if (latestOrder.getStatus() != null && latestOrder.getStatus() == STATUS_PAID) {
                        validateAlreadyPaidProviderTradeNo(latestOrder, callbackResult.getProviderTradeNo());
                        result = paymentSuccessHandlerRouter.getHandler(latestOrder.getProductType()).alreadyPaid(latestOrder);
                    } else {
                        throw new IllegalArgumentException("order status is not CREATED");
                    }
                } else {
                    order.setStatus(STATUS_PAID);
                    order.setProviderTradeNo(callbackResult.getProviderTradeNo());
                    order.setPaidAt(paidAt);
                    order.setUpdatedAt(paidAt);

                    result = paymentSuccessHandlerRouter.getHandler(order.getProductType()).finishPaidOrder(order);
                }
            }

            paymentCallbackRecordManager.markSuccess(callbackRecord.getId());
            return result;
        } catch (RuntimeException e) {
            try {
                paymentCallbackRecordManager.markFailed(callbackRecord.getId(), callbackErrorMessage(e));
            } catch (RuntimeException markFailedException) {
                e.addSuppressed(markFailedException);
            }
            throw e;
        }
    }

    public GetOrderResult getOrder(String orderNo) {
        validateOrderNo(orderNo);

        PaymentOrderEntity order = paymentOrderManager.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("order not found"));

        return new GetOrderResult(
                PaymentCoinService.CODE_OK,
                "ok",
                order.getOrderNo(),
                order.getUserId(),
                order.getProductType(),
                order.getProductCode(),
                order.getProvider(),
                order.getAmountCents(),
                order.getCurrency(),
                order.getCoinAmount(),
                order.getSubscriptionTier() == null ? "" : order.getSubscriptionTier(),
                order.getStatus(),
                order.getProviderTradeNo() == null ? "" : order.getProviderTradeNo(),
                toEpochMs(order.getPaidAt()),
                toEpochMs(order.getCreatedAt()),
                toEpochMs(order.getUpdatedAt())
        );
    }

    public ListUserOrdersResult listUserOrders(Long userId, Integer limit) {
        validateUserId(userId);

        int normalizedLimit = normalizeLimit(limit);

        List<OrderSummaryResult> orders = paymentOrderManager.listByUserId(userId, normalizedLimit)
                .stream()
                .map(this::toOrderSummaryResult)
                .toList();

        return new ListUserOrdersResult(
                PaymentCoinService.CODE_OK,
                "ok",
                userId,
                orders
        );
    }

    public ListProductsResult listProducts() {
        return new ListProductsResult(
                PaymentCoinService.CODE_OK,
                "ok",
                List.of(
                        new ProductItemResult("COIN_100", "COIN", "100 Coins", 99L, CURRENCY_USD, 100L, "", 0),
                        new ProductItemResult("COIN_500", "COIN", "500 Coins", 399L, CURRENCY_USD, 500L, "", 0),
                        new ProductItemResult("SUB_WEEKLY", "SUBSCRIPTION", "Weekly Subscription", 699L, CURRENCY_USD, 0L, "WEEKLY", 7),
                        new ProductItemResult("SUB_MONTHLY", "SUBSCRIPTION", "Monthly Subscription", 1999L, CURRENCY_USD, 0L, "MONTHLY", 30),
                        new ProductItemResult("SUB_YEARLY", "SUBSCRIPTION", "Yearly Subscription", 9999L, CURRENCY_USD, 0L, "YEARLY", 365)
                )
        );
    }

    public PreparePaymentResult preparePayment(String orderNo, String returnUrl, String cancelUrl) {
        validateOrderNo(orderNo);

        PaymentOrderEntity order = paymentOrderManager.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("order not found"));

        if (order.getStatus() == null || order.getStatus() != STATUS_CREATED) {
            throw new IllegalArgumentException("only CREATED order can prepare payment");
        }

        PreparePaymentProviderResult providerResult = paymentProviderRouter
                .getClient(order.getProvider())
                .preparePayment(new PreparePaymentCommand(
                        order.getOrderNo(),
                        order.getUserId(),
                        order.getProductType(),
                        order.getProductCode(),
                        order.getProvider(),
                        order.getAmountCents(),
                        order.getCurrency(),
                        returnUrl,
                        cancelUrl
                ));

        return new PreparePaymentResult(
                PaymentCoinService.CODE_OK,
                "ok",
                order.getOrderNo(),
                order.getUserId(),
                order.getProductType(),
                order.getProductCode(),
                order.getProvider(),
                order.getAmountCents(),
                order.getCurrency(),
                order.getStatus(),
                providerResult.getPayUrl(),
                providerResult.getProviderPayload(),
                providerResult.getExpireAtMs()
        );
    }

    public ListCallbackRecordsResult listCallbackRecords(String orderNo, String providerTradeNo, Integer limit) {
        validateCallbackQuery(orderNo, providerTradeNo);

        int normalizedLimit = normalizeLimit(limit);
        List<CallbackRecordResult> records = paymentCallbackRecordManager
                .listForDebug(orderNo, providerTradeNo, normalizedLimit)
                .stream()
                .map(this::toCallbackRecordResult)
                .toList();

        return new ListCallbackRecordsResult(
                PaymentCoinService.CODE_OK,
                "ok",
                records
        );
    }

    private PaymentCallbackRecordEntity createMockPaySuccessCallbackRecord(PaymentCallbackProviderResult callbackResult) {
        PaymentCallbackRecordEntity record = new PaymentCallbackRecordEntity();
        record.setProvider(callbackResult.getProvider());
        record.setEventType(callbackResult.getEventType());
        record.setOrderNo(callbackResult.getOrderNo());
        record.setProviderTradeNo(callbackResult.getProviderTradeNo());
        record.setPaidAmountCents(callbackResult.getPaidAmountCents());
        record.setCurrency(callbackResult.getCurrency());
        record.setRawPayload(callbackResult.getRawPayload());
        record.setProcessStatus(CALLBACK_STATUS_PROCESSING);
        record.setErrorMessage("");
        record.setCreatedAt(OffsetDateTime.now());

        return paymentCallbackRecordManager.create(record);
    }

    private String mockPaySuccessRawPayload(String orderNo,
                                            String providerTradeNo,
                                            Long paidAmountCents,
                                            String currency) {
        return "{\"orderNo\":\"" + orderNo
                + "\",\"providerTradeNo\":\"" + providerTradeNo
                + "\",\"paidAmountCents\":" + paidAmountCents
                + ",\"currency\":\"" + currency
                + "\"}";
    }

    private String callbackErrorMessage(RuntimeException e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return message.length() <= 512 ? message : message.substring(0, 512);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 20;
        }

        return Math.min(limit, 100);
    }

    private void validateCallbackQuery(String orderNo, String providerTradeNo) {
        boolean hasOrderNo = orderNo != null && !orderNo.isBlank();
        boolean hasProviderTradeNo = providerTradeNo != null && !providerTradeNo.isBlank();
        if (!hasOrderNo && !hasProviderTradeNo) {
            throw new IllegalArgumentException("order_no or provider_trade_no must not be blank");
        }
    }

    private CallbackRecordResult toCallbackRecordResult(PaymentCallbackRecordEntity record) {
        return new CallbackRecordResult(
                record.getId(),
                record.getProvider(),
                record.getEventType(),
                record.getOrderNo() == null ? "" : record.getOrderNo(),
                record.getProviderTradeNo() == null ? "" : record.getProviderTradeNo(),
                record.getPaidAmountCents() == null ? 0L : record.getPaidAmountCents(),
                record.getCurrency() == null ? "" : record.getCurrency(),
                record.getRawPayload(),
                record.getProcessStatus(),
                record.getErrorMessage() == null ? "" : record.getErrorMessage(),
                toEpochMs(record.getCreatedAt())
        );
    }

    private OrderSummaryResult toOrderSummaryResult(PaymentOrderEntity order) {
        return new OrderSummaryResult(
                order.getOrderNo(),
                order.getUserId(),
                order.getProductType(),
                order.getProductCode(),
                order.getProvider(),
                order.getAmountCents(),
                order.getCurrency(),
                order.getCoinAmount(),
                order.getSubscriptionTier() == null ? "" : order.getSubscriptionTier(),
                order.getStatus(),
                order.getProviderTradeNo() == null ? "" : order.getProviderTradeNo(),
                toEpochMs(order.getPaidAt()),
                toEpochMs(order.getClosedAt()),
                toEpochMs(order.getCreatedAt()),
                toEpochMs(order.getUpdatedAt())
        );
    }

    @Transactional
    public ExpireOrderResult closeExpiredOrder(String orderNo, Integer expireMinutes) {
        validateOrderNo(orderNo);

        int normalizedExpireMinutes = normalizeExpireMinutes(expireMinutes);
        OffsetDateTime now = OffsetDateTime.now();

        PaymentOrderEntity order = paymentOrderManager.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("order not found"));

        if (order.getStatus() != null && order.getStatus() == STATUS_CLOSED) {
            return expireOrderResult(order, "already closed", true);
        }

        if (order.getStatus() != null && order.getStatus() == STATUS_PAID) {
            throw new IllegalArgumentException("paid order cannot be expired closed");
        }

        if (order.getStatus() == null || order.getStatus() != STATUS_CREATED) {
            throw new IllegalArgumentException("order status is not CREATED");
        }

        if (!isOrderExpired(order, now, normalizedExpireMinutes)) {
            return expireOrderResult(order, "not expired", false);
        }

        int updated = paymentOrderManager.markClosedIfCreated(orderNo, now);
        if (updated == 0) {
            PaymentOrderEntity latestOrder = paymentOrderManager.findByOrderNo(orderNo)
                    .orElseThrow(() -> new IllegalArgumentException("order not found"));

            if (latestOrder.getStatus() != null && latestOrder.getStatus() == STATUS_CLOSED) {
                return expireOrderResult(latestOrder, "already closed", true);
            }

            if (latestOrder.getStatus() != null && latestOrder.getStatus() == STATUS_PAID) {
                throw new IllegalArgumentException("paid order cannot be expired closed");
            }

            throw new IllegalArgumentException("order status is not CREATED");
        }

        order.setStatus(STATUS_CLOSED);
        order.setClosedAt(now);
        order.setUpdatedAt(now);

        return expireOrderResult(order, "ok", true);
    }

    @Transactional
    public CloseExpiredOrdersResult closeExpiredOrders(Integer expireMinutes) {
        int normalizedExpireMinutes = normalizeExpireMinutes(expireMinutes);

        OffsetDateTime closedAt = OffsetDateTime.now();
        OffsetDateTime expiredBefore = closedAt.minusMinutes(normalizedExpireMinutes);

        int closedCount = paymentOrderManager.closeExpiredCreatedOrders(expiredBefore, closedAt);

        return new CloseExpiredOrdersResult(
                PaymentCoinService.CODE_OK,
                "ok",
                normalizedExpireMinutes,
                closedCount,
                toEpochMs(expiredBefore),
                toEpochMs(closedAt)
        );
    }

    private int normalizeExpireMinutes(Integer expireMinutes) {
        if (expireMinutes == null || expireMinutes <= 0) {
            return 30;
        }

        return expireMinutes;
    }

    private boolean isOrderExpired(PaymentOrderEntity order, OffsetDateTime now, int expireMinutes) {
        if (order.getCreatedAt() == null) {
            return false;
        }

        return !order.getCreatedAt().plusMinutes(expireMinutes).isAfter(now);
    }

    private ExpireOrderResult expireOrderResult(PaymentOrderEntity order, String message, Boolean expired) {
        return new ExpireOrderResult(
                PaymentCoinService.CODE_OK,
                message,
                order.getOrderNo(),
                order.getUserId(),
                order.getStatus(),
                expired,
                toEpochMs(order.getClosedAt())
        );
    }

    @Transactional
    public CloseOrderResult closeOrder(String orderNo) {
        validateOrderNo(orderNo);

        PaymentOrderEntity order = paymentOrderManager.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("order not found"));

        if (order.getStatus() != null && order.getStatus() == STATUS_CLOSED) {
            return closeOrderResult(order, "already closed");
        }

        if (order.getStatus() != null && order.getStatus() == STATUS_PAID) {
            throw new IllegalArgumentException("paid order cannot be closed");
        }

        if (order.getStatus() == null || order.getStatus() != STATUS_CREATED) {
            throw new IllegalArgumentException("order status is not CREATED");
        }

        OffsetDateTime closedAt = OffsetDateTime.now();
        int updated = paymentOrderManager.markClosedIfCreated(orderNo, closedAt);
        if (updated == 0) {
            PaymentOrderEntity latestOrder = paymentOrderManager.findByOrderNo(orderNo)
                    .orElseThrow(() -> new IllegalArgumentException("order not found"));

            if (latestOrder.getStatus() != null && latestOrder.getStatus() == STATUS_CLOSED) {
                return closeOrderResult(latestOrder, "already closed");
            }

            if (latestOrder.getStatus() != null && latestOrder.getStatus() == STATUS_PAID) {
                throw new IllegalArgumentException("paid order cannot be closed");
            }

            throw new IllegalArgumentException("order status is not CREATED");
        }

        order.setStatus(STATUS_CLOSED);
        order.setClosedAt(closedAt);
        order.setUpdatedAt(closedAt);

        return closeOrderResult(order, "ok");
    }

    private CloseOrderResult closeOrderResult(PaymentOrderEntity order, String message) {
        return new CloseOrderResult(
                PaymentCoinService.CODE_OK,
                message,
                order.getOrderNo(),
                order.getUserId(),
                order.getProductType(),
                order.getProductCode(),
                STATUS_CLOSED,
                toEpochMs(order.getClosedAt())
        );
    }

    private ProductSpec resolveProduct(String productCode) {
        if (productCode == null || productCode.isBlank()) {
            throw new IllegalArgumentException("product_code must not be blank");
        }

        String normalized = productCode.toUpperCase();

        if ("COIN_100".equals(normalized)) {
            return new ProductSpec("COIN", "COIN_100", 99L, 100L, "");
        }

        if ("COIN_500".equals(normalized)) {
            return new ProductSpec("COIN", "COIN_500", 399L, 500L, "");
        }

        if ("SUB_WEEKLY".equals(normalized)) {
            return new ProductSpec("SUBSCRIPTION", "SUB_WEEKLY", 699L, 0L, "WEEKLY");
        }

        if ("SUB_MONTHLY".equals(normalized)) {
            return new ProductSpec("SUBSCRIPTION", "SUB_MONTHLY", 1999L, 0L, "MONTHLY");
        }

        if ("SUB_YEARLY".equals(normalized)) {
            return new ProductSpec("SUBSCRIPTION", "SUB_YEARLY", 9999L, 0L, "YEARLY");
        }

        throw new IllegalArgumentException("unsupported product_code");
    }

    private String generateOrderNo() {
        return "pay_" + UUID.randomUUID().toString().replace("-", "");
    }

    private void validateAlreadyPaidProviderTradeNo(PaymentOrderEntity order, String providerTradeNo) {
        if (order.getProviderTradeNo() == null || order.getProviderTradeNo().isBlank()) {
            throw new IllegalArgumentException("paid order provider_trade_no is blank");
        }

        if (!order.getProviderTradeNo().equals(providerTradeNo)) {
            throw new IllegalArgumentException("paid order provider_trade_no does not match");
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("user_id must be positive");
        }
    }

    private void validateProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
    }

    private void validateOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException("order_no must not be blank");
        }
    }

    private void validateProviderTradeNo(String providerTradeNo) {
        if (providerTradeNo == null || providerTradeNo.isBlank()) {
            throw new IllegalArgumentException("provider_trade_no must not be blank");
        }
    }

    private void validatePaidAmountCents(Long paidAmountCents) {
        if (paidAmountCents == null || paidAmountCents <= 0) {
            throw new IllegalArgumentException("paid_amount_cents must be positive");
        }
    }

    private void validateCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
    }

    private void validatePaidOrderAmount(PaymentOrderEntity order, Long paidAmountCents, String currency) {
        if (!paidAmountCents.equals(order.getAmountCents())) {
            throw new IllegalArgumentException("paid amount does not match order amount");
        }

        if (!currency.equalsIgnoreCase(order.getCurrency())) {
            throw new IllegalArgumentException("currency does not match order currency");
        }
    }

    private void validateProviderTradeNoNotUsed(String orderNo, String providerTradeNo) {
        paymentOrderManager.findByProviderTradeNo(providerTradeNo)
                .ifPresent(existingOrder -> {
                    if (!existingOrder.getOrderNo().equals(orderNo)) {
                        throw new IllegalArgumentException("provider_trade_no already used by another order");
                    }
                });
    }

    private Long toEpochMs(OffsetDateTime time) {
        if (time == null) {
            return 0L;
        }

        return time.toInstant().toEpochMilli();
    }

    private record ProductSpec(String productType,
                               String productCode,
                               Long amountCents,
                               Long coinAmount,
                               String subscriptionTier) {
    }
}
