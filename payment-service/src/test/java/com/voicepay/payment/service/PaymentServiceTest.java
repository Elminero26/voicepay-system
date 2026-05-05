package com.voicepay.payment.service;

import com.voicepay.payment.dto.PaymentStats;
import com.voicepay.payment.model.Payment;
import com.voicepay.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService — Tests Unitarios")
@SuppressWarnings("null")
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private RestTemplate restTemplate;
    @Mock private PaymentGatewaySimulator paymentGatewaySimulator;

    @InjectMocks
    private PaymentService paymentService;

    private Payment completedPayment;
    private Payment failedPayment;

    @BeforeEach
    void setUp() {
        completedPayment = Payment.builder()
                .id(1L).userId(2L)
                .amount(new BigDecimal("25.00"))
                .status(Payment.PaymentStatus.COMPLETED)
                .description("Pago vía IVR")
                .build();

        failedPayment = Payment.builder()
                .id(2L).userId(2L)
                .amount(new BigDecimal("100.00"))
                .status(Payment.PaymentStatus.FAILED)
                .build();
    }

    // ─── getAllPayments ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllPayments — devuelve todos los pagos")
    void getAllPayments_returnsAll() {
        when(paymentRepository.findAll()).thenReturn(List.of(completedPayment, failedPayment));

        List<Payment> result = paymentService.getAllPayments();

        assertThat(result).hasSize(2);
        verify(paymentRepository, times(1)).findAll();
    }

    // ─── getPaymentsByUserId ─────────────────────────────────────────────────

    @Test
    @DisplayName("getPaymentsByUserId — devuelve pagos del usuario")
    void getPaymentsByUserId_returnsUserPayments() {
        when(paymentRepository.findByUserId(2L)).thenReturn(List.of(completedPayment));

        List<Payment> result = paymentService.getPaymentsByUserId(2L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(2L);
    }

    // ─── getRecentPayments ───────────────────────────────────────────────────

    @Test
    @DisplayName("getRecentPayments — devuelve los últimos 10 pagos")
    void getRecentPayments_returnsTop10() {
        when(paymentRepository.findTop10ByOrderByCreatedAtDesc())
                .thenReturn(List.of(completedPayment, failedPayment));

        List<Payment> result = paymentService.getRecentPayments();

        assertThat(result).hasSize(2);
    }

    // ─── getPaymentStats ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getPaymentStats — calcula completados, fallidos y monto total")
    void getPaymentStats_calculatesCorrectly() {
        when(paymentRepository.countByStatus(Payment.PaymentStatus.COMPLETED)).thenReturn(8L);
        when(paymentRepository.countByStatus(Payment.PaymentStatus.FAILED)).thenReturn(2L);
        when(paymentRepository.countByStatus(Payment.PaymentStatus.PENDING)).thenReturn(0L);
        when(paymentRepository.findAll()).thenReturn(List.of(completedPayment));

        PaymentStats stats = paymentService.getPaymentStats();

        assertThat(stats.getCompleted()).isEqualTo(8L);
        assertThat(stats.getFailed()).isEqualTo(2L);
        assertThat(stats.getPending()).isEqualTo(0L);
        assertThat(stats.getTotalAmount()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("getPaymentStats — totalAmount es 0 si no hay pagos completados")
    void getPaymentStats_totalAmountZero_whenNoCompleted() {
        when(paymentRepository.countByStatus(any())).thenReturn(0L);
        when(paymentRepository.findAll()).thenReturn(List.of(failedPayment));

        PaymentStats stats = paymentService.getPaymentStats();

        assertThat(stats.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ─── getPaymentById ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getPaymentById — devuelve pago cuando existe")
    void getPaymentById_returnsPayment() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(completedPayment));

        Payment result = paymentService.getPaymentById(1L);

        assertThat(result.getAmount()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("getPaymentById — lanza excepción si no existe")
    void getPaymentById_throwsException_whenNotFound() {
        when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentById(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Payment not found");
    }

    // ─── createPayment ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createPayment — pago exitoso cuando la pasarela aprueba")
    void createPayment_completesWhenGatewaySucceeds() {
        Payment newPayment = Payment.builder()
                .userId(2L).amount(new BigDecimal("25.00")).build();

        when(restTemplate.getForObject(any(String.class), eq(Object.class)))
                .thenReturn(new Object());
        when(paymentRepository.save(any(Payment.class))).thenReturn(completedPayment);
        when(paymentGatewaySimulator.processPayment(any())).thenReturn(true);

        Payment result = paymentService.createPayment(newPayment);

        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("createPayment — pago fallido cuando la pasarela rechaza")
    void createPayment_failsWhenGatewayRejects() {
        Payment newPayment = Payment.builder()
                .userId(2L).amount(new BigDecimal("100.00")).build();

        when(restTemplate.getForObject(any(String.class), eq(Object.class)))
                .thenReturn(new Object());
        when(paymentRepository.save(any(Payment.class))).thenReturn(failedPayment);
        when(paymentGatewaySimulator.processPayment(any())).thenReturn(false);

        Payment result = paymentService.createPayment(newPayment);

        assertThat(result.getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("createPayment — lanza excepción si el usuario no existe")
    void createPayment_throwsException_whenUserServiceFails() {
        Payment newPayment = Payment.builder()
                .userId(999L).amount(new BigDecimal("25.00")).build();

        when(restTemplate.getForObject(any(String.class), eq(Object.class)))
                .thenThrow(new RuntimeException("User service down"));

        assertThatThrownBy(() -> paymentService.createPayment(newPayment))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Validation failed");
    }
}
