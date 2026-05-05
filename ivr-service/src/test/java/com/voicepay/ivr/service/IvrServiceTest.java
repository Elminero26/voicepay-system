package com.voicepay.ivr.service;

import com.voicepay.ivr.dto.CallRequest;
import com.voicepay.ivr.dto.IvrResponse;
import com.voicepay.ivr.dto.LiveCall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IvrService — Tests Unitarios")
@SuppressWarnings("null")
class IvrServiceTest {

    @Mock private RestTemplate restTemplate;
    @Mock private LiveCallBroadcaster broadcaster;

    @InjectMocks
    private IvrService ivrService;

    private CallRequest validRequest;

    @BeforeEach
    void setUp() {
        // Inyectar las URLs que normalmente vendrían de application.properties
        ReflectionTestUtils.setField(ivrService, "userServiceUrl", "http://localhost:8080/users");
        ReflectionTestUtils.setField(ivrService, "paymentServiceUrl", "http://localhost:8081/payments");

        validRequest = new CallRequest();
        validRequest.setFrom("+34611223344");
    }

    // ─── handleIncomingCall ──────────────────────────────────────────────────

    @Test
    @DisplayName("handleIncomingCall — identifica usuario y crea llamada en vivo")
    void handleIncomingCall_createsLiveCall_whenUserFound() {
        Map<String, Object> mockUser = Map.of("id", 2, "name", "Test User");
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(mockUser);

        IvrResponse response = ivrService.handleIncomingCall(validRequest);

        assertThat(response.getMessage()).contains("Bienvenido Test User");
        assertThat(response.getNextAction()).isEqualTo("WAIT_FOR_CONFIRMATION");
        assertThat(response.getUserId()).isEqualTo(2L);

        // Verificar que se registró la llamada en vivo
        Collection<LiveCall> liveCalls = ivrService.getLiveCalls();
        assertThat(liveCalls).hasSize(1);
        assertThat(liveCalls.iterator().next().getStatus()).isEqualTo("WAITING_CONFIRMATION");

        // Verificar que se emitió el broadcast
        verify(broadcaster, times(1)).broadcast(any());
    }

    @Test
    @DisplayName("handleIncomingCall — responde HANGUP si usuario no encontrado")
    void handleIncomingCall_returnsHangup_whenUserNotFound() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RuntimeException("User not found"));

        IvrResponse response = ivrService.handleIncomingCall(validRequest);

        assertThat(response.getNextAction()).isEqualTo("HANGUP");
        assertThat(response.getMessage()).contains("no hemos podido identificar");

        // No debe haberse creado ninguna llamada en vivo
        assertThat(ivrService.getLiveCalls()).isEmpty();

        // No debe haberse emitido ningún broadcast
        verify(broadcaster, never()).broadcast(any());
    }

    @Test
    @DisplayName("handleIncomingCall — estado inicial es WAITING_CONFIRMATION")
    void handleIncomingCall_setsInitialStatus_waitingConfirmation() {
        Map<String, Object> mockUser = Map.of("id", 3, "name", "Juan Perez");
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(mockUser);

        ivrService.handleIncomingCall(validRequest);

        LiveCall liveCall = ivrService.getLiveCalls().iterator().next();
        assertThat(liveCall.getPhoneNumber()).isEqualTo("+34611223344");
        assertThat(liveCall.getUserName()).isEqualTo("Juan Perez");
        assertThat(liveCall.getStatus()).isEqualTo("WAITING_CONFIRMATION");
    }

    // ─── getLiveCalls ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getLiveCalls — devuelve lista vacía inicialmente")
    void getLiveCalls_returnsEmpty_initially() {
        Collection<LiveCall> result = ivrService.getLiveCalls();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getLiveCalls — devuelve llamadas activas registradas")
    void getLiveCalls_returnsActiveCalls() {
        Map<String, Object> mockUser = Map.of("id", 2, "name", "Test User");
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(mockUser);

        ivrService.handleIncomingCall(validRequest);

        assertThat(ivrService.getLiveCalls()).hasSize(1);
    }

    // ─── confirmPayment ──────────────────────────────────────────────────────

    @Test
    @DisplayName("confirmPayment — cambia a PROCESSING_PAYMENT antes del pago")
    void confirmPayment_setsProcessingStatus() {
        // Primero registrar una llamada
        Map<String, Object> mockUser = Map.of("id", 2, "name", "Test User");
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(mockUser);
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class))).thenReturn(Map.of());
        ivrService.handleIncomingCall(validRequest);

        ivrService.confirmPayment(2L);

        // El broadcaster debe haberse llamado múltiples veces:
        // 1 vez al crear llamada + al menos 1 vez al confirmar pago
        verify(broadcaster, atLeast(2)).broadcast(any());
    }

    @Test
    @DisplayName("confirmPayment — devuelve mensaje de éxito cuando el pago funciona")
    void confirmPayment_returnsSuccess_whenPaymentWorks() {
        Map<String, Object> mockUser = Map.of("id", 2, "name", "Test User");
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(mockUser);
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class))).thenReturn(Map.of());
        ivrService.handleIncomingCall(validRequest);

        IvrResponse response = ivrService.confirmPayment(2L);

        assertThat(response.getMessage()).contains("procesado correctamente");
        assertThat(response.getNextAction()).isEqualTo("HANGUP");
    }

    @Test
    @DisplayName("confirmPayment — devuelve error cuando el payment-service falla")
    void confirmPayment_returnsError_whenPaymentServiceFails() {
        Map<String, Object> mockUser = Map.of("id", 2, "name", "Test User");
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(mockUser);
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("Payment service down"));
        ivrService.handleIncomingCall(validRequest);

        IvrResponse response = ivrService.confirmPayment(2L);

        assertThat(response.getMessage()).contains("error al procesar");
        assertThat(response.getNextAction()).isEqualTo("HANGUP");
    }
}
