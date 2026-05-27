package com.voicepay.payment.service;

import com.voicepay.payment.model.Payment;
import com.voicepay.payment.repository.PaymentRepository;
import com.voicepay.payment.dto.PaymentStats;
import com.voicepay.payment.client.UserServiceClient;
import com.voicepay.payment.client.NotificationServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
@SuppressWarnings("null")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private NotificationServiceClient notificationServiceClient;

    @Mock
    private PaymentGatewaySimulator paymentGatewaySimulator;

    @Mock
    private com.voicepay.payment.security.JwtUtil jwtUtil;

    @Mock
    private CurrencyExchangeService currencyExchangeService;

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

        // Mock default behavior of jwtUtil
        lenient().when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("dummy-token");

        // Mock default behavior of currencyExchangeService
        lenient().when(currencyExchangeService.getRate(anyString())).thenReturn(BigDecimal.ONE);
        lenient().when(currencyExchangeService.convert(any(), anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("completePaymentByUserId — Success Scenario")
    void whenPaymentSucceeds_thenUpdateToCompletedAndNotify() {
        // GIVEN
        when(paymentRepository.findByUserId(100L)).thenReturn(Collections.singletonList(pendingPayment));
        when(paymentGatewaySimulator.processPayment(any(), any())).thenReturn(true);
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        // Mock User Service Client (for the name in notification)
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", 100L);
        userMap.put("name", "Cristian Test");
        
        when(userServiceClient.getUserDetails(eq(100L), any())).thenReturn(userMap);

        // WHEN
        Payment result = paymentService.completePaymentByUserId(100L);

        // THEN
        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
        assertThat(result.getTransactionId()).startsWith("TX-");
        
        // Verify Notification was sent
        verify(notificationServiceClient, times(1)).sendNotification(any(), any());
        verify(paymentRepository, times(1)).save(any(Payment.class));
    }

    @Test
    @DisplayName("completePaymentByUserId — Failure Scenario")
    void whenPaymentFails_thenUpdateToFailedAndNotify() {
        // GIVEN
        when(paymentRepository.findByUserId(100L)).thenReturn(Collections.singletonList(pendingPayment));
        when(paymentGatewaySimulator.processPayment(any(), any())).thenReturn(false);
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
        when(userServiceClient.validateUser(eq(100L), any())).thenReturn(new Object());
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
