package com.voicepay.ivr.client;

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

import com.voicepay.ivr.dto.CampaignMemberDto;
import com.voicepay.ivr.dto.PageResponse;

@Slf4j
@Component
@RequiredArgsConstructor
@SuppressWarnings("null")
public class UserServiceClient {

    private final RestTemplate restTemplate;

    @Value("${app.user-service.url}")
    private String userServiceUrl;

    @SuppressWarnings("unchecked")
    @CircuitBreaker(name = "userService", fallbackMethod = "fallbackGetUserByPhone")
    @Retry(name = "userService")
    public Map<String, Object> getUserByPhone(String phone, HttpHeaders headers) {
        log.info("Calling User Service for phone: {}", phone);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(
                userServiceUrl + "/phone/" + phone,
                HttpMethod.GET,
                entity,
                Map.class).getBody();
    }

    public Map<String, Object> fallbackGetUserByPhone(String phone, HttpHeaders headers, Throwable t) {
        log.warn("Fallback triggered for getUserByPhone due to error: {}", t.getMessage());
        Map<String, Object> fallbackUser = new HashMap<>();
        fallbackUser.put("id", -1L);
        fallbackUser.put("name", "Usuario Temporal");
        fallbackUser.put("phone", phone);
        return fallbackUser;
    }

    public PageResponse<CampaignMemberDto> getPendingCampaignMembers(int page, int size, HttpHeaders headers) {
        log.info("Calling User Service for pending campaign members - page: {}, size: {}", page, size);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        String baseUrl = userServiceUrl;
        if (baseUrl.endsWith("/users")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 6);
        }
        String url = baseUrl + "/campaigns/members/pending?page=" + page + "&size=" + size;
        return restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                new org.springframework.core.ParameterizedTypeReference<PageResponse<CampaignMemberDto>>() {}).getBody();
    }

    public CampaignMemberDto updateCampaignMemberStatus(Long memberId, String status, HttpHeaders headers) {
        log.info("Calling User Service to update campaign member {} status to {}", memberId, status);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        String baseUrl = userServiceUrl;
        if (baseUrl.endsWith("/users")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 6);
        }
        String url = baseUrl + "/campaigns/members/" + memberId + "/status?status=" + status;
        return restTemplate.exchange(
                url,
                HttpMethod.PUT,
                entity,
                CampaignMemberDto.class).getBody();
    }
}
