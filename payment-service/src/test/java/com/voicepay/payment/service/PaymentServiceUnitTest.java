package com.voicepay.payment.service;

import com.voicepay.payment.model.Payment;
import com.voicepay.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
public class PaymentServiceUnitTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGatewaySimulator paymentGatewaySimulator;

    @Mock
    private com.voicepay.payment.client.UserServiceClient userServiceClient;

    @Mock
    private com.voicepay.payment.client.NotificationServiceClient notificationServiceClient;

    @Mock
    private CurrencyExchangeService currencyExchangeService;

    @Mock
    private com.voicepay.payment.security.JwtUtil jwtUtil;

    @InjectMocks
    private PaymentService paymentService;

    private Payment pendingPayment;

    @BeforeEach
    void setUp() {
        pendingPayment = new Payment();
        pendingPayment.setId(1L);
        pendingPayment.setUserId(3L);
        pendingPayment.setAmount(new BigDecimal("50.0"));
        pendingPayment.setStatus(Payment.PaymentStatus.PENDING);

        // Mock lenient user client to avoid NPE on notifications
        lenient().when(userServiceClient.getUserDetails(anyLong(), any())).thenReturn(java.util.Map.of("name", "Usuario"));

        // Mock lenient behavior for jwtUtil and currencyExchangeService
        lenient().when(jwtUtil.generateToken(any(), any())).thenReturn("mocked-token");
        lenient().when(currencyExchangeService.getRate(any())).thenReturn(BigDecimal.ONE);
        lenient().when(currencyExchangeService.convert(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void completePaymentByUserId_ShouldSucceed_WhenPendingPaymentExists() {
        // 1. CONFIGURACIÓN (MOCKITO)
        // Decimos: "Cuando busques por el ID 3, devuelve una lista con nuestro pago pendiente"
        when(paymentRepository.findByUserId(3L)).thenReturn(List.of(pendingPayment));
        // Decimos: "Cuando el banco procese el pago, di que ha tenido éxito"
        when(paymentGatewaySimulator.processPayment(any(), any())).thenReturn(true);
        // Decimos: "Cuando guardes el resultado, simplemente devuelve lo que recibas"
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArguments()[0]);

        // 2. EJECUCIÓN
        Payment result = paymentService.completePaymentByUserId(3L);

        // 3. VERIFICACIÓN (JUNIT)
        assertNotNull(result);
        assertEquals(Payment.PaymentStatus.COMPLETED, result.getStatus());
        
        // Verificamos que se llamó al banco exactamente 1 vez
        verify(paymentGatewaySimulator, times(1)).processPayment(any(), any());
        // Verificamos que se guardó en la base de datos
        verify(paymentRepository, times(1)).save(any(Payment.class));
    }

    @Test
    void completePaymentByUserId_ShouldFail_WhenNoPendingPayment() {
        // Configuración: El usuario 3 no tiene pagos
        when(paymentRepository.findByUserId(3L)).thenReturn(List.of());

        // Verificación: Debería lanzar una excepción
        assertThrows(RuntimeException.class, () -> {
            paymentService.completePaymentByUserId(3L);
        });
    }
}
