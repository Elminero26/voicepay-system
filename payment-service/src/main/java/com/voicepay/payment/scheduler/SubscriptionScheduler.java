package com.voicepay.payment.scheduler;

import com.voicepay.payment.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionScheduler {

    private final SubscriptionService subscriptionService;

    // Runs every minute to find due subscriptions and process them automatically.
    // Cron: 0 * * * * ? (At second 0 of every minute)
    @Scheduled(cron = "0 * * * * ?")
    public void processRecurringPayments() {
        log.info("SubscriptionScheduler: starting scheduled check for due recurring payments...");
        try {
            subscriptionService.processDueSubscriptions();
        } catch (Exception e) {
            log.error("SubscriptionScheduler: error occurred while processing due subscriptions: {}", e.getMessage(), e);
        }
        log.info("SubscriptionScheduler: scheduled check completed.");
    }
}
