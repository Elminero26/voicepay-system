package com.voicepay.ivr.service;

import com.voicepay.ivr.dto.CallRequest;
import com.voicepay.ivr.dto.IvrResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IvrService Unit Tests")
@SuppressWarnings({"null", "unchecked"})
class IvrServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private LiveCallBroadcaster broadcaster;

    @Mock
    private com.voicepay.ivr.repository.LiveCallRepository callRepository;

    @Mock
    private com.voicepay.ivr.security.JwtUtil jwtUtil;

    @InjectMocks
    private IvrService ivrService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ivrService, "userServiceUrl", "http://user-service");
        ReflectionTestUtils.setField(ivrService, "paymentServiceUrl", "http://payment-service");
        
        // Mock default behavior of jwtUtil
        lenient().when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("dummy-token");
    }

    @Test
    @DisplayName("handleIncomingCall — Should identify user and show payment amount")
    void whenIncomingCall_thenIdentifyUserAndBroadcast() {
        // GIVEN
        CallRequest request = new CallRequest();
        request.setFrom("+34666000111");

        // Mock User Service
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", 1);
        userMap.put("name", "Cristian");
        when(restTemplate.exchange(contains("/phone/"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(userMap));

        // Mock Payment Service
        Map<String, Object> paymentMap = new HashMap<>();
        paymentMap.put("amount", 75.50);
        when(restTemplate.exchange(contains("/pending/"), eq(HttpMethod.GET), any(HttpEntity.class), any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(paymentMap));

        // WHEN
        IvrResponse response = ivrService.handleIncomingCall(request);

        // THEN
        assertThat(response.getMessage()).contains("Cristian");
        assertThat(response.getMessage()).contains("75.5");
        
        // Verify broadcast to dashboard
        verify(broadcaster, times(1)).broadcast(anyCollection());
    }

    @Test
    @DisplayName("confirmPayment — Should update status to COMPLETED")
    void whenConfirmingPayment_thenStatusIsCompleted() {
        // GIVEN
        Long userId = 1L;
        
        // Mock successful payment confirmation
        when(restTemplate.exchange(contains("/confirm/"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(new HashMap<>()));

        // WHEN
        IvrResponse response = ivrService.confirmPayment(userId);

        // THEN
        assertThat(response.getMessage()).contains("procesado correctamente");
    }
}
