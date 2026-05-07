package com.voicepay.payment.service;

import com.voicepay.payment.model.Payment;
import com.voicepay.payment.repository.PaymentRepository;
import com.voicepay.payment.dto.PaymentStats;
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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Unit Tests")
@SuppressWarnings("unchecked")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private PaymentGatewaySimulator paymentGatewaySimulator;

    @InjectMocks
    private PaymentService paymentService;

    private Payment pendingPayment;

    @BeforeEach
    void setUp() {
        pendingPayment = Payment.builder()
                .id(1L)
                .userId(100L)
                .amount(new BigDecimal("50.00"))
                .build();
        pendingPayment.setCurrency("EUR");
        pendingPayment.setStatus(Payment.PaymentStatus.PENDING);

        // Inyectamos valores manuales para @Value
        ReflectionTestUtils.setField(paymentService, "userServiceUrl", "http://user-service");
        ReflectionTestUtils.setField(paymentService, "notificationServiceUrl", "http://notification-service");
        ReflectionTestUtils.setField(paymentService, "apiKey", "test-api-key");
    }

    @Test
    @DisplayName("completePaymentByUserId — Success Scenario")
    void whenPaymentSucceeds_thenUpdateToCompletedAndNotify() {
        // GIVEN
        when(paymentRepository.findByUserId(100L)).thenReturn(Collections.singletonList(pendingPayment));
        when(paymentGatewaySimulator.processPayment(any())).thenReturn(true);
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // Mock User Service (for the name in notification)
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", 100);
        userMap.put("name", "Richard Test");
        ResponseEntity<Map> userResponse = ResponseEntity.ok(userMap);
        
        when(restTemplate.exchange(contains("/users/100"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(userResponse);

        // WHEN
        Payment result = paymentService.completePaymentByUserId(100L);

        // THEN
        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
        assertThat(result.getTransactionId()).startsWith("TX-");
        
        // Verify Notification was sent
        verify(restTemplate, times(1)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
        verify(paymentRepository, times(1)).save(any(Payment.class));
    }

    @Test
    @DisplayName("completePaymentByUserId — Failure Scenario")
    void whenPaymentFails_thenUpdateToFailedAndNotify() {
        // GIVEN
        when(paymentRepository.findByUserId(100L)).thenReturn(Collections.singletonList(pendingPayment));
        when(paymentGatewaySimulator.processPayment(any())).thenReturn(false);
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // WHEN
        Payment result = paymentService.completePaymentByUserId(100L);

        // THEN
        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);
        assertThat(result.getTransactionId()).contains("FAILED");
        
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("createPayment — Saves as PENDING after validation")
    void whenCreatingPayment_thenStatusIsPending() {
        // GIVEN
        Payment newPayment = Payment.builder().userId(100L).amount(new BigDecimal("10.00")).build();
        
        // Mock User Validation
        when(restTemplate.exchange(contains("/100"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok(new Object()));
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // WHEN
        Payment result = paymentService.createPayment(newPayment);

        // THEN
        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.PENDING);
        verify(paymentRepository).save(newPayment);
    }

    @Test
    @DisplayName("getPaymentStats — Correct calculation")
    void getPaymentStats_calculatesProperly() {
        // GIVEN
        when(paymentRepository.countByStatus(Payment.PaymentStatus.COMPLETED)).thenReturn(5L);
        when(paymentRepository.countByStatus(Payment.PaymentStatus.FAILED)).thenReturn(2L);
        when(paymentRepository.countByStatus(Payment.PaymentStatus.PENDING)).thenReturn(1L);
        
        Payment p1 = Payment.builder().status(Payment.PaymentStatus.COMPLETED).amount(new BigDecimal("100")).build();
        Payment p2 = Payment.builder().status(Payment.PaymentStatus.COMPLETED).amount(new BigDecimal("50")).build();
        when(paymentRepository.findAll()).thenReturn(List.of(p1, p2));

        // WHEN
        PaymentStats stats = paymentService.getPaymentStats();

        // THEN
        assertThat(stats.getCompleted()).isEqualTo(5L);
        assertThat(stats.getTotalAmount()).isEqualByComparingTo("150.00");
    }
}
