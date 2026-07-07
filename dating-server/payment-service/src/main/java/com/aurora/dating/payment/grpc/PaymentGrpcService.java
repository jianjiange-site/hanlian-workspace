package com.aurora.dating.payment.grpc;

import com.aurora.dating.payment.error.PaymentExceptionTranslator;

import com.dating.hanlian.proto.payment.v1.PaymentServiceGrpc;
import com.dating.hanlian.proto.payment.v1.PingRequest;
import com.dating.hanlian.proto.payment.v1.PingResponse;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;
import com.aurora.dating.payment.entity.PaymentCoinAccountEntity;
import com.aurora.dating.payment.service.CloseOrderResult;
import com.aurora.dating.payment.service.ConsumeCoinsResult;
import com.aurora.dating.payment.service.CloseExpiredOrdersResult;
import com.aurora.dating.payment.service.CreateOrderResult;
import com.aurora.dating.payment.service.ExpireOrderResult;
import com.aurora.dating.payment.service.GetOrderResult;
import com.aurora.dating.payment.service.GrantCoinsResult;
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
import com.dating.hanlian.proto.payment.v1.CloseOrderRequest;
import com.dating.hanlian.proto.payment.v1.CloseOrderResponse;
import com.dating.hanlian.proto.payment.v1.CloseExpiredOrderRequest;
import com.dating.hanlian.proto.payment.v1.CloseExpiredOrderResponse;
import com.dating.hanlian.proto.payment.v1.CloseExpiredOrdersRequest;
import com.dating.hanlian.proto.payment.v1.CloseExpiredOrdersResponse;
import com.dating.hanlian.proto.payment.v1.ConsumeCoinsRequest;
import com.dating.hanlian.proto.payment.v1.ConsumeCoinsResponse;
import com.dating.hanlian.proto.payment.v1.CreateOrderRequest;
import com.dating.hanlian.proto.payment.v1.CreateOrderResponse;
import com.dating.hanlian.proto.payment.v1.GetCoinsRequest;
import com.dating.hanlian.proto.payment.v1.GetCoinsResponse;
import com.dating.hanlian.proto.payment.v1.GetOrderRequest;
import com.dating.hanlian.proto.payment.v1.GetOrderResponse;
import com.dating.hanlian.proto.payment.v1.GetSubscriptionRequest;
import com.dating.hanlian.proto.payment.v1.GetSubscriptionResponse;
import com.dating.hanlian.proto.payment.v1.GrantCoinsRequest;
import com.dating.hanlian.proto.payment.v1.GrantCoinsResponse;
import com.dating.hanlian.proto.payment.v1.ListProductsRequest;
import com.dating.hanlian.proto.payment.v1.ListProductsResponse;
import com.dating.hanlian.proto.payment.v1.ListUserOrdersRequest;
import com.dating.hanlian.proto.payment.v1.ListUserOrdersResponse;
import com.dating.hanlian.proto.payment.v1.MockPaySuccessRequest;
import com.dating.hanlian.proto.payment.v1.MockPaySuccessResponse;
import com.dating.hanlian.proto.payment.v1.OrderSummary;
import com.dating.hanlian.proto.payment.v1.ProductItem;
import com.dating.hanlian.proto.payment.v1.PreparePaymentRequest;
import com.dating.hanlian.proto.payment.v1.PreparePaymentResponse;

@Component
public class PaymentGrpcService extends PaymentServiceGrpc.PaymentServiceImplBase {

    private final PaymentCoinService paymentCoinService;
    private final PaymentSubscriptionService paymentSubscriptionService;
    private final PaymentOrderService paymentOrderService;

    public PaymentGrpcService(PaymentCoinService paymentCoinService,
                              PaymentSubscriptionService paymentSubscriptionService,
                              PaymentOrderService paymentOrderService) {
        this.paymentCoinService = paymentCoinService;
        this.paymentSubscriptionService = paymentSubscriptionService;
        this.paymentOrderService = paymentOrderService;
    }

    /**
     * 查用户余额
     * @param request
     * @param responseObserver
     */
    @Override
    public void getCoins(GetCoinsRequest request, StreamObserver<GetCoinsResponse> responseObserver) {
        try {
            PaymentCoinAccountEntity account = paymentCoinService.getCoins(request.getUserId());

            GetCoinsResponse response = GetCoinsResponse.newBuilder()
                    .setCode(PaymentCoinService.CODE_OK)
                    .setMessage("ok")
                    .setUserId(account.getUserId())
                    .setBalance(account.getBalance())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            GetCoinsResponse response = GetCoinsResponse.newBuilder()
                    .setCode(PaymentExceptionTranslator.translate(e).getCode())
                    .setMessage(PaymentExceptionTranslator.translate(e).getMessage())
                    .setUserId(request.getUserId())
                    .setBalance(0)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    /**
     * 加金币
     * @param request
     * @param responseObserver
     */
    @Override
    public void grantCoins(GrantCoinsRequest request, StreamObserver<GrantCoinsResponse> responseObserver) {
        try {
            GrantCoinsResult result = paymentCoinService.grantCoins(
                    request.getUserId(),
                    request.getAmount(),
                    request.getReason(),
                    request.getIdempotencyKey()
            );

            GrantCoinsResponse response = GrantCoinsResponse.newBuilder()
                    .setCode(result.getCode())
                    .setMessage(result.getMessage())
                    .setUserId(request.getUserId())
                    .setBalance(result.getBalance())
                    .setLedgerNo(result.getLedgerNo())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            GrantCoinsResponse response = GrantCoinsResponse.newBuilder()
                    .setCode(PaymentExceptionTranslator.translate(e).getCode())
                    .setMessage(PaymentExceptionTranslator.translate(e).getMessage())
                    .setUserId(request.getUserId())
                    .setBalance(0)
                    .setLedgerNo("")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    /**
     * 扣金币
     * @param request
     * @param responseObserver
     */
    @Override
    public void consumeCoins(ConsumeCoinsRequest request, StreamObserver<ConsumeCoinsResponse> responseObserver) {
        try {
            ConsumeCoinsResult result = paymentCoinService.consumeCoins(
                    request.getUserId(),
                    request.getAmount(),
                    request.getReason(),
                    request.getIdempotencyKey()
            );

            ConsumeCoinsResponse response = ConsumeCoinsResponse.newBuilder()
                    .setCode(result.getCode())
                    .setMessage(result.getMessage())
                    .setUserId(request.getUserId())
                    .setBalance(result.getBalance())
                    .setLedgerNo(result.getLedgerNo())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            ConsumeCoinsResponse response = ConsumeCoinsResponse.newBuilder()
                    .setCode(PaymentExceptionTranslator.translate(e).getCode())
                    .setMessage(PaymentExceptionTranslator.translate(e).getMessage())
                    .setUserId(request.getUserId())
                    .setBalance(0)
                    .setLedgerNo("")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    /**
     * 查用户订阅权益
     * @param request
     * @param responseObserver
     */
    @Override
    public void getSubscription(GetSubscriptionRequest request,
                                StreamObserver<GetSubscriptionResponse> responseObserver) {
        try {
            SubscriptionResult result = paymentSubscriptionService.getSubscription(request.getUserId());

            GetSubscriptionResponse response = GetSubscriptionResponse.newBuilder()
                    .setCode(result.getCode())
                    .setMessage(result.getMessage())
                    .setUserId(result.getUserId())
                    .setTier(result.getTier())
                    .setActive(result.getActive())
                    .setExpireAtMs(result.getExpireAtMs())
                    .setDailyRightSwipeLimit(result.getDailyRightSwipeLimit())
                    .setDailyCardLimit(result.getDailyCardLimit())
                    .setDailySuperHiLimit(result.getDailySuperHiLimit())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            GetSubscriptionResponse response = GetSubscriptionResponse.newBuilder()
                    .setCode(PaymentExceptionTranslator.translate(e).getCode())
                    .setMessage(PaymentExceptionTranslator.translate(e).getMessage())
                    .setUserId(request.getUserId())
                    .setTier("FREE")
                    .setActive(false)
                    .setExpireAtMs(0)
                    .setDailyRightSwipeLimit(20)
                    .setDailyCardLimit(50)
                    .setDailySuperHiLimit(0)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    /**
     * 创建金币/订阅订单
     * @param request
     * @param responseObserver
     */
    @Override
    public void createOrder(CreateOrderRequest request,
                            StreamObserver<CreateOrderResponse> responseObserver) {
        try {
            CreateOrderResult result = paymentOrderService.createOrder(
                    request.getUserId(),
                    request.getProductCode(),
                    request.getProvider()
            );

            CreateOrderResponse response = CreateOrderResponse.newBuilder()
                    .setCode(result.getCode())
                    .setMessage(result.getMessage())
                    .setOrderNo(result.getOrderNo())
                    .setUserId(result.getUserId())
                    .setProductType(result.getProductType())
                    .setProductCode(result.getProductCode())
                    .setProvider(result.getProvider())
                    .setAmountCents(result.getAmountCents())
                    .setCurrency(result.getCurrency())
                    .setCoinAmount(result.getCoinAmount())
                    .setSubscriptionTier(result.getSubscriptionTier())
                    .setStatus(result.getStatus())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            CreateOrderResponse response = CreateOrderResponse.newBuilder()
                    .setCode(PaymentExceptionTranslator.translate(e).getCode())
                    .setMessage(PaymentExceptionTranslator.translate(e).getMessage())
                    .setOrderNo("")
                    .setUserId(request.getUserId())
                    .setProductType("")
                    .setProductCode(request.getProductCode())
                    .setProvider(request.getProvider())
                    .setAmountCents(0)
                    .setCurrency("")
                    .setCoinAmount(0)
                    .setSubscriptionTier("")
                    .setStatus(0)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    /**
     * 模拟支付成功回调
     * @param request
     * @param responseObserver
     */
    @Override
    public void mockPaySuccess(MockPaySuccessRequest request,
                               StreamObserver<MockPaySuccessResponse> responseObserver) {
        try {
            PaySuccessResult result = paymentOrderService.mockPaySuccess(
                    request.getOrderNo(),
                    request.getProviderTradeNo(),
                    request.getPaidAmountCents(),
                    request.getCurrency()
            );

            MockPaySuccessResponse response = MockPaySuccessResponse.newBuilder()
                    .setCode(result.getCode())
                    .setMessage(result.getMessage())
                    .setOrderNo(result.getOrderNo())
                    .setUserId(result.getUserId())
                    .setProductType(result.getProductType())
                    .setProductCode(result.getProductCode())
                    .setProviderTradeNo(result.getProviderTradeNo())
                    .setStatus(result.getStatus())
                    .setBalance(result.getBalance())
                    .setLedgerNo(result.getLedgerNo())
                    .setTier(result.getTier())
                    .setActive(result.getActive())
                    .setExpireAtMs(result.getExpireAtMs())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            MockPaySuccessResponse response = MockPaySuccessResponse.newBuilder()
                    .setCode(PaymentExceptionTranslator.translate(e).getCode())
                    .setMessage(PaymentExceptionTranslator.translate(e).getMessage())
                    .setOrderNo(request.getOrderNo())
                    .setUserId(0)
                    .setProductType("")
                    .setProductCode("")
                    .setProviderTradeNo(request.getProviderTradeNo())
                    .setStatus(0)
                    .setBalance(0)
                    .setLedgerNo("")
                    .setTier("")
                    .setActive(false)
                    .setExpireAtMs(0)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    /**
     * 创建支付拉起参数
     * @param request
     * @param responseObserver
     */
    @Override
    public void preparePayment(PreparePaymentRequest request,
                               StreamObserver<PreparePaymentResponse> responseObserver) {
        try {
            PreparePaymentResult result = paymentOrderService.preparePayment(
                    request.getOrderNo(),
                    request.getReturnUrl(),
                    request.getCancelUrl()
            );

            PreparePaymentResponse response = PreparePaymentResponse.newBuilder()
                    .setCode(result.getCode())
                    .setMessage(result.getMessage())
                    .setOrderNo(result.getOrderNo())
                    .setUserId(result.getUserId())
                    .setProductType(result.getProductType())
                    .setProductCode(result.getProductCode())
                    .setProvider(result.getProvider())
                    .setAmountCents(result.getAmountCents())
                    .setCurrency(result.getCurrency())
                    .setStatus(result.getStatus())
                    .setPayUrl(result.getPayUrl())
                    .setProviderPayload(result.getProviderPayload())
                    .setExpireAtMs(result.getExpireAtMs())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            PreparePaymentResponse response = PreparePaymentResponse.newBuilder()
                    .setCode(PaymentExceptionTranslator.translate(e).getCode())
                    .setMessage(PaymentExceptionTranslator.translate(e).getMessage())
                    .setOrderNo(request.getOrderNo())
                    .setUserId(0)
                    .setProductType("")
                    .setProductCode("")
                    .setProvider("")
                    .setAmountCents(0)
                    .setCurrency("")
                    .setStatus(0)
                    .setPayUrl("")
                    .setProviderPayload("")
                    .setExpireAtMs(0)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    /**
     * 查询订单详情
     * @param request
     * @param responseObserver
     */
    @Override
    public void getOrder(GetOrderRequest request, StreamObserver<GetOrderResponse> responseObserver) {
        try {
            GetOrderResult result = paymentOrderService.getOrder(request.getOrderNo());

            GetOrderResponse response = GetOrderResponse.newBuilder()
                    .setCode(result.getCode())
                    .setMessage(result.getMessage())
                    .setOrderNo(result.getOrderNo())
                    .setUserId(result.getUserId())
                    .setProductType(result.getProductType())
                    .setProductCode(result.getProductCode())
                    .setProvider(result.getProvider())
                    .setAmountCents(result.getAmountCents())
                    .setCurrency(result.getCurrency())
                    .setCoinAmount(result.getCoinAmount())
                    .setSubscriptionTier(result.getSubscriptionTier())
                    .setStatus(result.getStatus())
                    .setProviderTradeNo(result.getProviderTradeNo())
                    .setPaidAtMs(result.getPaidAtMs())
                    .setCreatedAtMs(result.getCreatedAtMs())
                    .setUpdatedAtMs(result.getUpdatedAtMs())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            GetOrderResponse response = GetOrderResponse.newBuilder()
                    .setCode(PaymentExceptionTranslator.translate(e).getCode())
                    .setMessage(PaymentExceptionTranslator.translate(e).getMessage())
                    .setOrderNo(request.getOrderNo())
                    .setUserId(0)
                    .setProductType("")
                    .setProductCode("")
                    .setProvider("")
                    .setAmountCents(0)
                    .setCurrency("")
                    .setCoinAmount(0)
                    .setSubscriptionTier("")
                    .setStatus(0)
                    .setProviderTradeNo("")
                    .setPaidAtMs(0)
                    .setCreatedAtMs(0)
                    .setUpdatedAtMs(0)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    /**
     * 关闭未支付订单
     * @param request
     * @param responseObserver
     */
    @Override
    public void closeOrder(CloseOrderRequest request, StreamObserver<CloseOrderResponse> responseObserver) {
        try {
            CloseOrderResult result = paymentOrderService.closeOrder(request.getOrderNo());

            CloseOrderResponse response = CloseOrderResponse.newBuilder()
                    .setCode(result.getCode())
                    .setMessage(result.getMessage())
                    .setOrderNo(result.getOrderNo())
                    .setUserId(result.getUserId())
                    .setProductType(result.getProductType())
                    .setProductCode(result.getProductCode())
                    .setStatus(result.getStatus())
                    .setClosedAtMs(result.getClosedAtMs())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            CloseOrderResponse response = CloseOrderResponse.newBuilder()
                    .setCode(PaymentExceptionTranslator.translate(e).getCode())
                    .setMessage(PaymentExceptionTranslator.translate(e).getMessage())
                    .setOrderNo(request.getOrderNo())
                    .setUserId(0)
                    .setProductType("")
                    .setProductCode("")
                    .setStatus(0)
                    .setClosedAtMs(0)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    /**
     * 关闭已过期未支付订单
     * @param request
     * @param responseObserver
     */
    @Override
    public void closeExpiredOrder(CloseExpiredOrderRequest request,
                                  StreamObserver<CloseExpiredOrderResponse> responseObserver) {
        try {
            ExpireOrderResult result = paymentOrderService.closeExpiredOrder(
                    request.getOrderNo(),
                    request.getExpireMinutes()
            );

            CloseExpiredOrderResponse response = CloseExpiredOrderResponse.newBuilder()
                    .setCode(result.getCode())
                    .setMessage(result.getMessage())
                    .setOrderNo(result.getOrderNo())
                    .setUserId(result.getUserId())
                    .setStatus(result.getStatus())
                    .setExpired(result.getExpired())
                    .setClosedAtMs(result.getClosedAtMs())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            CloseExpiredOrderResponse response = CloseExpiredOrderResponse.newBuilder()
                    .setCode(PaymentExceptionTranslator.translate(e).getCode())
                    .setMessage(PaymentExceptionTranslator.translate(e).getMessage())
                    .setOrderNo(request.getOrderNo())
                    .setUserId(0)
                    .setStatus(0)
                    .setExpired(false)
                    .setClosedAtMs(0)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    /**
     * 批量关闭已过期未支付订单
     * @param request
     * @param responseObserver
     */
    @Override
    public void closeExpiredOrders(CloseExpiredOrdersRequest request,
                                   StreamObserver<CloseExpiredOrdersResponse> responseObserver) {
        CloseExpiredOrdersResult result = paymentOrderService.closeExpiredOrders(request.getExpireMinutes());

        CloseExpiredOrdersResponse response = CloseExpiredOrdersResponse.newBuilder()
                .setCode(result.getCode())
                .setMessage(result.getMessage())
                .setExpireMinutes(result.getExpireMinutes())
                .setClosedCount(result.getClosedCount())
                .setExpiredBeforeMs(result.getExpiredBeforeMs())
                .setClosedAtMs(result.getClosedAtMs())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * 查询用户订单列表
     * @param request
     * @param responseObserver
     */
    @Override
    public void listUserOrders(ListUserOrdersRequest request,
                               StreamObserver<ListUserOrdersResponse> responseObserver) {
        try {
            ListUserOrdersResult result = paymentOrderService.listUserOrders(
                    request.getUserId(),
                    request.getLimit()
            );

            ListUserOrdersResponse.Builder responseBuilder = ListUserOrdersResponse.newBuilder()
                    .setCode(result.getCode())
                    .setMessage(result.getMessage())
                    .setUserId(result.getUserId());

            for (OrderSummaryResult order : result.getOrders()) {
                responseBuilder.addOrders(toOrderSummary(order));
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            ListUserOrdersResponse response = ListUserOrdersResponse.newBuilder()
                    .setCode(PaymentExceptionTranslator.translate(e).getCode())
                    .setMessage(PaymentExceptionTranslator.translate(e).getMessage())
                    .setUserId(request.getUserId())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    private OrderSummary toOrderSummary(OrderSummaryResult order) {
        return OrderSummary.newBuilder()
                .setOrderNo(order.getOrderNo())
                .setUserId(order.getUserId())
                .setProductType(order.getProductType())
                .setProductCode(order.getProductCode())
                .setProvider(order.getProvider())
                .setAmountCents(order.getAmountCents())
                .setCurrency(order.getCurrency())
                .setCoinAmount(order.getCoinAmount())
                .setSubscriptionTier(order.getSubscriptionTier())
                .setStatus(order.getStatus())
                .setProviderTradeNo(order.getProviderTradeNo())
                .setPaidAtMs(order.getPaidAtMs())
                .setClosedAtMs(order.getClosedAtMs())
                .setCreatedAtMs(order.getCreatedAtMs())
                .setUpdatedAtMs(order.getUpdatedAtMs())
                .build();
    }

    /**
     * 查询支付商品列表
     * @param request
     * @param responseObserver
     */
    @Override
    public void listProducts(ListProductsRequest request,
                             StreamObserver<ListProductsResponse> responseObserver) {
        ListProductsResult result = paymentOrderService.listProducts();

        ListProductsResponse.Builder responseBuilder = ListProductsResponse.newBuilder()
                .setCode(result.getCode())
                .setMessage(result.getMessage());

        for (ProductItemResult product : result.getProducts()) {
            responseBuilder.addProducts(toProductItem(product));
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    private ProductItem toProductItem(ProductItemResult product) {
        return ProductItem.newBuilder()
                .setProductCode(product.getProductCode())
                .setProductType(product.getProductType())
                .setTitle(product.getTitle())
                .setAmountCents(product.getAmountCents())
                .setCurrency(product.getCurrency())
                .setCoinAmount(product.getCoinAmount())
                .setSubscriptionTier(product.getSubscriptionTier())
                .setSubscriptionDays(product.getSubscriptionDays())
                .build();
    }

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        PingResponse response = PingResponse.newBuilder()
                .setMessage("payment-service pong: " + request.getMessage())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}

