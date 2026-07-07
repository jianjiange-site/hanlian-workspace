package com.aurora.dating.payment.job;

import com.aurora.dating.payment.service.CloseExpiredOrdersResult;
import com.aurora.dating.payment.service.PaymentOrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentOrderExpireJob {

    private static final int DEFAULT_EXPIRE_MINUTES = 30;

    private final PaymentOrderService paymentOrderService;

    public PaymentOrderExpireJob(PaymentOrderService paymentOrderService) {
        this.paymentOrderService = paymentOrderService;
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "UTC")
    public void closeExpiredOrders() {
        CloseExpiredOrdersResult result = paymentOrderService.closeExpiredOrders(DEFAULT_EXPIRE_MINUTES);

        if (result.getClosedCount() > 0) {
            System.out.println("[payment-order-expire-job] closed expired orders, count="
                    + result.getClosedCount()
                    + ", expireMinutes="
                    + result.getExpireMinutes()
                    + ", expiredBeforeMs="
                    + result.getExpiredBeforeMs()
                    + ", closedAtMs="
                    + result.getClosedAtMs());
        }
    }
}
