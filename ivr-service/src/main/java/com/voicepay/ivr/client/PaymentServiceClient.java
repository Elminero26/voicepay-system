package com.voicepay.ivr.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
import java.util.HashMap;
import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentServiceClient {

    private final RestTemplate restTemplate;

    @Value("${app.payment-service.url}")
    private String paymentServiceUrl;

    @CircuitBreaker(name = "paymentService", fallbackMethod = "fallbackGetPendingPayment")
    @Retry(name = "paymentService")
    public Map<String, Object> getPendingPayment(Long userId, HttpHeaders headers) {
        log.info("Calling Payment Service for pending payment of user: {}", userId);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                paymentServiceUrl + "/pending/" + userId,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
        return response.getBody();
    }

    public Map<String, Object> fallbackGetPendingPayment(Long userId, HttpHeaders headers, Throwable t) {
        log.warn("Fallback triggered for getPendingPayment of user {} due to error: {}", userId, t.getMessage());
        Map<String, Object> fallbackPayment = new HashMap<>();
        fallbackPayment.put("amount", new BigDecimal("25.00")); // Valor de seguridad por defecto
        fallbackPayment.put("userId", userId);
        fallbackPayment.put("status", "PENDING");
        return fallbackPayment;
    }

    @SuppressWarnings("unchecked")
    @CircuitBreaker(name = "paymentService", fallbackMethod = "fallbackConfirmPayment")
    @Retry(name = "paymentService")
    public Map<String, Object> confirmPayment(Long userId, HttpHeaders headers) {
        log.info("Calling Payment Service to confirm payment of user: {}", userId);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(
                paymentServiceUrl + "/confirm/" + userId,
                HttpMethod.POST,
                entity,
                Map.class).getBody();
    }

    public Map<String, Object> fallbackConfirmPayment(Long userId, HttpHeaders headers, Throwable t) {
        log.warn("Fallback triggered for confirmPayment of user {} due to error: {}", userId, t.getMessage());
        Map<String, Object> fallbackResponse = new HashMap<>();
        fallbackResponse.put("status", "FAILED_GATEWAY");
        fallbackResponse.put("message", "Payment service unavailable, handled by fallback.");
        return fallbackResponse;
    }
}
