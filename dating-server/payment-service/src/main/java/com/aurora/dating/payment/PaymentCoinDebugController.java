package com.aurora.dating.payment;

import com.aurora.dating.payment.error.PaymentExceptionTranslator;

import com.aurora.dating.payment.entity.PaymentCoinAccountEntity;
import com.aurora.dating.payment.service.CloseOrderResult;
import com.aurora.dating.payment.service.CallbackRecordResult;
import com.aurora.dating.payment.service.ConsumeCoinsResult;
import com.aurora.dating.payment.service.CreateOrderResult;
import com.aurora.dating.payment.service.CloseExpiredOrdersResult;
import com.aurora.dating.payment.service.ExpireOrderResult;
import com.aurora.dating.payment.service.GetOrderResult;
import com.aurora.dating.payment.service.GrantCoinsResult;
import com.aurora.dating.payment.service.ListCallbackRecordsResult;
import com.aurora.dating.payment.service.ListProductsResult;
import com.aurora.dating.payment.service.ListUserOrdersResult;
import com.aurora.dating.payment.service.OrderSummaryResult;
import com.aurora.dating.payment.service.PaySuccessResult;
import com.aurora.dating.payment.service.PaymentCoinService;
import com.aurora.dating.payment.service.PaymentOrderService;
import com.aurora.dating.payment.service.PaymentSubscriptionService;
import com.aurora.dating.payment.service.PreparePaymentResult;
import com.aurora.dating.payment.service.ProductItemResult;
import com.aurora.dating.payment.service.SubscriptionResult;
import java.util.List;
import java.util.Map;
import static java.util.Map.entry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentCoinDebugController {

    private final PaymentCoinService paymentCoinService;
    private final PaymentSubscriptionService paymentSubscriptionService;
    private final PaymentOrderService paymentOrderService;

    public PaymentCoinDebugController(PaymentCoinService paymentCoinService,
                                      PaymentSubscriptionService paymentSubscriptionService,
                                      PaymentOrderService paymentOrderService) {
        this.paymentCoinService = paymentCoinService;
        this.paymentSubscriptionService = paymentSubscriptionService;
        this.paymentOrderService = paymentOrderService;
    }

    @GetMapping("/internal/debug/payment/coins")
    public Map<String, Object> getCoins(Long userId) {
        try {
            PaymentCoinAccountEntity account = paymentCoinService.getCoins(userId);

            return Map.of(
                    "code", PaymentCoinService.CODE_OK,
                    "message", "ok",
                    "userId", account.getUserId(),
                    "balance", account.getBalance()
            );
        } catch (RuntimeException e) {
            return Map.of(
                    "code", PaymentExceptionTranslator.translate(e).getCode(),
                    "message", PaymentExceptionTranslator.translate(e).getMessage(),
                    "userId", userId == null ? 0 : userId,
                    "balance", 0
            );
        }
    }

    @GetMapping("/internal/debug/payment/grant")
    public Map<String, Object> grantCoins(Long userId, Long amount, String reason, String idempotencyKey) {
        try {
            GrantCoinsResult result = paymentCoinService.grantCoins(userId, amount, reason, idempotencyKey);

            return Map.of(
                    "code", result.getCode(),
                    "message", result.getMessage(),
                    "userId", userId,
                    "balance", result.getBalance(),
                    "ledgerNo", result.getLedgerNo()
            );
        } catch (RuntimeException e) {
            return Map.of(
                    "code", PaymentExceptionTranslator.translate(e).getCode(),
                    "message", PaymentExceptionTranslator.translate(e).getMessage(),
                    "userId", userId == null ? 0 : userId,
                    "balance", 0,
                    "ledgerNo", ""
            );
        }
    }

    @GetMapping("/internal/debug/payment/consume")
    public Map<String, Object> consumeCoins(Long userId, Long amount, String reason, String idempotencyKey) {
        try {
            ConsumeCoinsResult result = paymentCoinService.consumeCoins(userId, amount, reason, idempotencyKey);

            return Map.of(
                    "code", result.getCode(),
                    "message", result.getMessage(),
                    "userId", userId,
                    "balance", result.getBalance(),
                    "ledgerNo", result.getLedgerNo()
            );
        } catch (RuntimeException e) {
            return Map.of(
                    "code", PaymentExceptionTranslator.translate(e).getCode(),
                    "message", PaymentExceptionTranslator.translate(e).getMessage(),
                    "userId", userId == null ? 0 : userId,
                    "balance", 0,
                    "ledgerNo", ""
            );
        }
    }

    @GetMapping("/internal/debug/payment/subscription")
    public Map<String, Object> getSubscription(Long userId) {
        try {
            SubscriptionResult result = paymentSubscriptionService.getSubscription(userId);

            return Map.of(
                    "code", result.getCode(),
                    "message", result.getMessage(),
                    "userId", result.getUserId(),
                    "tier", result.getTier(),
                    "active", result.getActive(),
                    "expireAtMs", result.getExpireAtMs(),
                    "dailyRightSwipeLimit", result.getDailyRightSwipeLimit(),
                    "dailyCardLimit", result.getDailyCardLimit(),
                    "dailySuperHiLimit", result.getDailySuperHiLimit()
            );
        } catch (RuntimeException e) {
            return Map.of(
                    "code", PaymentExceptionTranslator.translate(e).getCode(),
                    "message", PaymentExceptionTranslator.translate(e).getMessage(),
                    "userId", userId == null ? 0 : userId,
                    "tier", "FREE",
                    "active", false,
                    "expireAtMs", 0,
                    "dailyRightSwipeLimit", 20,
                    "dailyCardLimit", 50,
                    "dailySuperHiLimit", 0
            );
        }
    }

    @GetMapping("/internal/debug/payment/subscription/set")
    public Map<String, Object> setSubscription(Long userId, String tier, Integer expireDays) {
        try {
            SubscriptionResult result = paymentSubscriptionService.setSubscription(userId, tier, expireDays);

            return Map.of(
                    "code", result.getCode(),
                    "message", result.getMessage(),
                    "userId", result.getUserId(),
                    "tier", result.getTier(),
                    "active", result.getActive(),
                    "expireAtMs", result.getExpireAtMs(),
                    "dailyRightSwipeLimit", result.getDailyRightSwipeLimit(),
                    "dailyCardLimit", result.getDailyCardLimit(),
                    "dailySuperHiLimit", result.getDailySuperHiLimit()
            );
        } catch (RuntimeException e) {
            return Map.of(
                    "code", PaymentExceptionTranslator.translate(e).getCode(),
                    "message", PaymentExceptionTranslator.translate(e).getMessage(),
                    "userId", userId == null ? 0 : userId,
                    "tier", "FREE",
                    "active", false,
                    "expireAtMs", 0,
                    "dailyRightSwipeLimit", 20,
                    "dailyCardLimit", 50,
                    "dailySuperHiLimit", 0
            );
        }
    }

    @GetMapping("/internal/debug/payment/order/create")
    public Map<String, Object> createOrder(Long userId, String productCode, String provider) {
        try {
            CreateOrderResult result = paymentOrderService.createOrder(userId, productCode, provider);

            return Map.ofEntries(
                    entry("code", result.getCode()),
                    entry("message", result.getMessage()),
                    entry("orderNo", result.getOrderNo()),
                    entry("userId", result.getUserId()),
                    entry("productType", result.getProductType()),
                    entry("productCode", result.getProductCode()),
                    entry("provider", result.getProvider()),
                    entry("amountCents", result.getAmountCents()),
                    entry("currency", result.getCurrency()),
                    entry("coinAmount", result.getCoinAmount()),
                    entry("subscriptionTier", result.getSubscriptionTier()),
                    entry("status", result.getStatus())
            );
        } catch (RuntimeException e) {
            return Map.ofEntries(
                    entry("code", PaymentExceptionTranslator.translate(e).getCode()),
                    entry("message", PaymentExceptionTranslator.translate(e).getMessage()),
                    entry("orderNo", ""),
                    entry("userId", userId == null ? 0 : userId),
                    entry("productType", ""),
                    entry("productCode", productCode == null ? "" : productCode),
                    entry("provider", provider == null ? "" : provider),
                    entry("amountCents", 0),
                    entry("currency", ""),
                    entry("coinAmount", 0),
                    entry("subscriptionTier", ""),
                    entry("status", 0)
            );
        }
    }

    @GetMapping("/internal/debug/payment/order/mock-pay-success")
    public Map<String, Object> mockPaySuccess(String orderNo,
                                             String providerTradeNo,
                                             Long paidAmountCents,
                                             String currency) {
        try {
            PaySuccessResult result = paymentOrderService.mockPaySuccess(
                    orderNo,
                    providerTradeNo,
                    paidAmountCents,
                    currency
            );

            return Map.ofEntries(
                    entry("code", result.getCode()),
                    entry("message", result.getMessage()),
                    entry("orderNo", result.getOrderNo()),
                    entry("userId", result.getUserId()),
                    entry("productType", result.getProductType()),
                    entry("productCode", result.getProductCode()),
                    entry("providerTradeNo", result.getProviderTradeNo()),
                    entry("status", result.getStatus()),
                    entry("balance", result.getBalance()),
                    entry("ledgerNo", result.getLedgerNo()),
                    entry("tier", result.getTier()),
                    entry("active", result.getActive()),
                    entry("expireAtMs", result.getExpireAtMs())
            );
        } catch (RuntimeException e) {
            return Map.ofEntries(
                    entry("code", PaymentExceptionTranslator.translate(e).getCode()),
                    entry("message", PaymentExceptionTranslator.translate(e).getMessage()),
                    entry("orderNo", orderNo == null ? "" : orderNo),
                    entry("userId", 0),
                    entry("productType", ""),
                    entry("productCode", ""),
                    entry("providerTradeNo", providerTradeNo == null ? "" : providerTradeNo),
                    entry("status", 0),
                    entry("balance", 0),
                    entry("ledgerNo", ""),
                    entry("tier", ""),
                    entry("active", false),
                    entry("expireAtMs", 0)
            );
        }
    }

    @GetMapping("/internal/debug/payment/order/prepare")
    public Map<String, Object> preparePayment(String orderNo, String returnUrl, String cancelUrl) {
        try {
            PreparePaymentResult result = paymentOrderService.preparePayment(orderNo, returnUrl, cancelUrl);

            return Map.ofEntries(
                    entry("code", result.getCode()),
                    entry("message", result.getMessage()),
                    entry("orderNo", result.getOrderNo()),
                    entry("userId", result.getUserId()),
                    entry("productType", result.getProductType()),
                    entry("productCode", result.getProductCode()),
                    entry("provider", result.getProvider()),
                    entry("amountCents", result.getAmountCents()),
                    entry("currency", result.getCurrency()),
                    entry("status", result.getStatus()),
                    entry("payUrl", result.getPayUrl()),
                    entry("providerPayload", result.getProviderPayload()),
                    entry("expireAtMs", result.getExpireAtMs())
            );
        } catch (RuntimeException e) {
            return Map.ofEntries(
                    entry("code", PaymentExceptionTranslator.translate(e).getCode()),
                    entry("message", PaymentExceptionTranslator.translate(e).getMessage()),
                    entry("orderNo", orderNo == null ? "" : orderNo),
                    entry("userId", 0),
                    entry("productType", ""),
                    entry("productCode", ""),
                    entry("provider", ""),
                    entry("amountCents", 0),
                    entry("currency", ""),
                    entry("status", 0),
                    entry("payUrl", ""),
                    entry("providerPayload", ""),
                    entry("expireAtMs", 0)
            );
        }
    }

    @GetMapping("/internal/debug/payment/order/get")
    public Map<String, Object> getOrder(String orderNo) {
        try {
            GetOrderResult result = paymentOrderService.getOrder(orderNo);

            return Map.ofEntries(
                    entry("code", result.getCode()),
                    entry("message", result.getMessage()),
                    entry("orderNo", result.getOrderNo()),
                    entry("userId", result.getUserId()),
                    entry("productType", result.getProductType()),
                    entry("productCode", result.getProductCode()),
                    entry("provider", result.getProvider()),
                    entry("amountCents", result.getAmountCents()),
                    entry("currency", result.getCurrency()),
                    entry("coinAmount", result.getCoinAmount()),
                    entry("subscriptionTier", result.getSubscriptionTier()),
                    entry("status", result.getStatus()),
                    entry("providerTradeNo", result.getProviderTradeNo()),
                    entry("paidAtMs", result.getPaidAtMs()),
                    entry("createdAtMs", result.getCreatedAtMs()),
                    entry("updatedAtMs", result.getUpdatedAtMs())
            );
        } catch (RuntimeException e) {
            return Map.ofEntries(
                    entry("code", PaymentExceptionTranslator.translate(e).getCode()),
                    entry("message", PaymentExceptionTranslator.translate(e).getMessage()),
                    entry("orderNo", orderNo == null ? "" : orderNo),
                    entry("userId", 0),
                    entry("productType", ""),
                    entry("productCode", ""),
                    entry("provider", ""),
                    entry("amountCents", 0),
                    entry("currency", ""),
                    entry("coinAmount", 0),
                    entry("subscriptionTier", ""),
                    entry("status", 0),
                    entry("providerTradeNo", ""),
                    entry("paidAtMs", 0),
                    entry("createdAtMs", 0),
                    entry("updatedAtMs", 0)
            );
        }
    }

    @GetMapping("/internal/debug/payment/order/close")
    public Map<String, Object> closeOrder(String orderNo) {
        try {
            CloseOrderResult result = paymentOrderService.closeOrder(orderNo);

            return Map.ofEntries(
                    entry("code", result.getCode()),
                    entry("message", result.getMessage()),
                    entry("orderNo", result.getOrderNo()),
                    entry("userId", result.getUserId()),
                    entry("productType", result.getProductType()),
                    entry("productCode", result.getProductCode()),
                    entry("status", result.getStatus()),
                    entry("closedAtMs", result.getClosedAtMs())
            );
        } catch (RuntimeException e) {
            return Map.ofEntries(
                    entry("code", PaymentExceptionTranslator.translate(e).getCode()),
                    entry("message", PaymentExceptionTranslator.translate(e).getMessage()),
                    entry("orderNo", orderNo == null ? "" : orderNo),
                    entry("userId", 0),
                    entry("productType", ""),
                    entry("productCode", ""),
                    entry("status", 0),
                    entry("closedAtMs", 0)
            );
        }
    }

    @GetMapping("/internal/debug/payment/order/close-expired")
    public Map<String, Object> closeExpiredOrder(String orderNo, Integer expireMinutes) {
        try {
            ExpireOrderResult result = paymentOrderService.closeExpiredOrder(orderNo, expireMinutes);

            return Map.ofEntries(
                    entry("code", result.getCode()),
                    entry("message", result.getMessage()),
                    entry("orderNo", result.getOrderNo()),
                    entry("userId", result.getUserId()),
                    entry("status", result.getStatus()),
                    entry("expired", result.getExpired()),
                    entry("closedAtMs", result.getClosedAtMs())
            );
        } catch (RuntimeException e) {
            return Map.ofEntries(
                    entry("code", PaymentExceptionTranslator.translate(e).getCode()),
                    entry("message", PaymentExceptionTranslator.translate(e).getMessage()),
                    entry("orderNo", orderNo == null ? "" : orderNo),
                    entry("userId", 0),
                    entry("status", 0),
                    entry("expired", false),
                    entry("closedAtMs", 0)
            );
        }
    }

    @GetMapping("/internal/debug/payment/order/close-expired-batch")
    public Map<String, Object> closeExpiredOrders(Integer expireMinutes) {
        CloseExpiredOrdersResult result = paymentOrderService.closeExpiredOrders(expireMinutes);

        return Map.ofEntries(
                entry("code", result.getCode()),
                entry("message", result.getMessage()),
                entry("expireMinutes", result.getExpireMinutes()),
                entry("closedCount", result.getClosedCount()),
                entry("expiredBeforeMs", result.getExpiredBeforeMs()),
                entry("closedAtMs", result.getClosedAtMs())
        );
    }

    @GetMapping("/internal/debug/payment/order/list")
    public Map<String, Object> listUserOrders(Long userId, Integer limit) {
        try {
            ListUserOrdersResult result = paymentOrderService.listUserOrders(userId, limit);

            return Map.ofEntries(
                    entry("code", result.getCode()),
                    entry("message", result.getMessage()),
                    entry("userId", result.getUserId()),
                    entry("orders", toOrderMaps(result.getOrders()))
            );
        } catch (RuntimeException e) {
            return Map.ofEntries(
                    entry("code", PaymentExceptionTranslator.translate(e).getCode()),
                    entry("message", PaymentExceptionTranslator.translate(e).getMessage()),
                    entry("userId", userId == null ? 0 : userId),
                    entry("orders", List.of())
            );
        }
    }

    private List<Map<String, Object>> toOrderMaps(List<OrderSummaryResult> orders) {
        return orders.stream()
                .map(order -> Map.<String, Object>ofEntries(
                        entry("orderNo", order.getOrderNo()),
                        entry("userId", order.getUserId()),
                        entry("productType", order.getProductType()),
                        entry("productCode", order.getProductCode()),
                        entry("provider", order.getProvider()),
                        entry("amountCents", order.getAmountCents()),
                        entry("currency", order.getCurrency()),
                        entry("coinAmount", order.getCoinAmount()),
                        entry("subscriptionTier", order.getSubscriptionTier()),
                        entry("status", order.getStatus()),
                        entry("providerTradeNo", order.getProviderTradeNo()),
                        entry("paidAtMs", order.getPaidAtMs()),
                        entry("closedAtMs", order.getClosedAtMs()),
                        entry("createdAtMs", order.getCreatedAtMs()),
                        entry("updatedAtMs", order.getUpdatedAtMs())
                ))
                .toList();
    }

    @GetMapping("/internal/debug/payment/callback-records")
    public Map<String, Object> listCallbackRecords(String orderNo, String providerTradeNo, Integer limit) {
        try {
            ListCallbackRecordsResult result = paymentOrderService.listCallbackRecords(orderNo, providerTradeNo, limit);

            return Map.ofEntries(
                    entry("code", result.getCode()),
                    entry("message", result.getMessage()),
                    entry("records", toCallbackRecordMaps(result.getRecords()))
            );
        } catch (RuntimeException e) {
            return Map.ofEntries(
                    entry("code", PaymentExceptionTranslator.translate(e).getCode()),
                    entry("message", PaymentExceptionTranslator.translate(e).getMessage()),
                    entry("records", List.of())
            );
        }
    }

    private List<Map<String, Object>> toCallbackRecordMaps(List<CallbackRecordResult> records) {
        return records.stream()
                .map(record -> Map.<String, Object>ofEntries(
                        entry("id", record.getId()),
                        entry("provider", record.getProvider()),
                        entry("eventType", record.getEventType()),
                        entry("orderNo", record.getOrderNo()),
                        entry("providerTradeNo", record.getProviderTradeNo()),
                        entry("paidAmountCents", record.getPaidAmountCents()),
                        entry("currency", record.getCurrency()),
                        entry("rawPayload", record.getRawPayload()),
                        entry("processStatus", record.getProcessStatus()),
                        entry("errorMessage", record.getErrorMessage()),
                        entry("createdAtMs", record.getCreatedAtMs())
                ))
                .toList();
    }

    @GetMapping("/internal/debug/payment/products")
    public Map<String, Object> listProducts() {
        ListProductsResult result = paymentOrderService.listProducts();

        return Map.ofEntries(
                entry("code", result.getCode()),
                entry("message", result.getMessage()),
                entry("products", toProductMaps(result.getProducts()))
        );
    }

    private List<Map<String, Object>> toProductMaps(List<ProductItemResult> products) {
        return products.stream()
                .map(product -> Map.<String, Object>ofEntries(
                        entry("productCode", product.getProductCode()),
                        entry("productType", product.getProductType()),
                        entry("title", product.getTitle()),
                        entry("amountCents", product.getAmountCents()),
                        entry("currency", product.getCurrency()),
                        entry("coinAmount", product.getCoinAmount()),
                        entry("subscriptionTier", product.getSubscriptionTier()),
                        entry("subscriptionDays", product.getSubscriptionDays())
                ))
                .toList();
    }
}

