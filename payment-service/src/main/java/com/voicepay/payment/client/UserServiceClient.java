package com.voicepay.payment.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@Component
@RequiredArgsConstructor
@SuppressWarnings("null")
public class UserServiceClient {

    private final RestTemplate restTemplate;

    @Value("${app.user-service.url}")
    private String userServiceUrl;

    @CircuitBreaker(name = "userService", fallbackMethod = "fallbackValidateUser")
    @Retry(name = "userService")
    public Object validateUser(Long userId, HttpHeaders headers) {
        log.info("Calling User Service to validate user: {}", userId);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(
                userServiceUrl + "/" + userId,
                HttpMethod.GET,
                entity,
                Object.class).getBody();
    }

    public Object fallbackValidateUser(Long userId, HttpHeaders headers, Throwable t) {
        log.warn("Fallback triggered for validateUser of user {} due to error: {}", userId, t.getMessage());
        // En caso de que falle, dejamos pasar el pago (fallback optimista para que no falle la creación)
        return new Object();
    }

    @SuppressWarnings("unchecked")
    @CircuitBreaker(name = "userService", fallbackMethod = "fallbackGetUserDetails")
    @Retry(name = "userService")
    public Map<String, Object> getUserDetails(Long userId, HttpHeaders headers) {
        log.info("Calling User Service for user details: {}", userId);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(
                userServiceUrl + "/" + userId,
                HttpMethod.GET,
                entity,
                Map.class).getBody();
    }

    public Map<String, Object> fallbackGetUserDetails(Long userId, HttpHeaders headers, Throwable t) {
        log.warn("Fallback triggered for getUserDetails of user {} due to error: {}", userId, t.getMessage());
        Map<String, Object> fallbackUser = new HashMap<>();
        fallbackUser.put("id", userId);
        fallbackUser.put("name", "Usuario");
        return fallbackUser;
    }

    @CircuitBreaker(name = "userService")
    @Retry(name = "userService")
    public Object createCampaign(Object campaignRequest, HttpHeaders headers) {
        log.info("Calling User Service to create campaign");
        String baseUrl = userServiceUrl;
        if (baseUrl.endsWith("/users")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 6);
        }
        String url = baseUrl + "/campaigns";
        HttpEntity<Object> entity = new HttpEntity<>(campaignRequest, headers);
        return restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Object.class).getBody();
    }

    @CircuitBreaker(name = "userService")
    @Retry(name = "userService")
    public Object updateCampaignStatus(Long campaignId, String status, HttpHeaders headers) {
        log.info("Calling User Service to update campaign {} status to {}", campaignId, status);
        String baseUrl = userServiceUrl;
        if (baseUrl.endsWith("/users")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 6);
        }
        String url = baseUrl + "/campaigns/" + campaignId + "/status?status=" + status;
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(
                url,
                HttpMethod.PUT,
                entity,
                Object.class).getBody();
    }

    @CircuitBreaker(name = "userService")
    @Retry(name = "userService")
    @SuppressWarnings("rawtypes")
    public Map getCampaignsReport(HttpHeaders headers) {
        log.info("Calling User Service to get campaign reports");
        String baseUrl = userServiceUrl;
        if (baseUrl.endsWith("/users")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 6);
        }
        String url = baseUrl + "/campaigns/reports";
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                Map.class).getBody();
    }
}
