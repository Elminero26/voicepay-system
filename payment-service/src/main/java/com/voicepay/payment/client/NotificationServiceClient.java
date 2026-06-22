package com.voicepay.payment.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@SuppressWarnings("null")
public class NotificationServiceClient {

    private final RestTemplate restTemplate;

    @Value("${app.notification-service.url}")
    private String notificationServiceUrl;

    @CircuitBreaker(name = "notificationService", fallbackMethod = "fallbackSendNotification")
    @Retry(name = "notificationService")
    public void sendNotification(Map<String, String> notificationRequest, HttpHeaders headers) {
        log.info("Calling Notification Service to send notification");
        HttpEntity<Map<String, String>> request = new HttpEntity<>(notificationRequest, headers);
        restTemplate.postForEntity(notificationServiceUrl, request, String.class);
    }

    public void fallbackSendNotification(Map<String, String> notificationRequest, HttpHeaders headers, Throwable t) {
        log.error("Fallback triggered for sendNotification due to error: {}. Notification content: {}", t.getMessage(), notificationRequest);
        // La notificación es no-bloqueante y secundaria, así que no propagamos el error para no abortar el flujo principal de pago.
    }

    @CircuitBreaker(name = "notificationService", fallbackMethod = "fallbackSendDunningNotification")
    @Retry(name = "notificationService")
    public void sendDunningNotification(Map<String, Object> dunningRequest, HttpHeaders headers) {
        log.info("Calling Notification Service to send dunning notification");
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(dunningRequest, headers);
        restTemplate.postForEntity(notificationServiceUrl + "/dunning", request, String.class);
    }

    public void fallbackSendDunningNotification(Map<String, Object> dunningRequest, HttpHeaders headers, Throwable t) {
        log.error("Fallback triggered for sendDunningNotification due to error: {}. Dunning request content: {}", t.getMessage(), dunningRequest);
    }
}
