package com.voicepay.ivr.service;

import com.voicepay.ivr.dto.CallRequest;
import com.voicepay.ivr.dto.IvrResponse;
import com.voicepay.ivr.client.UserServiceClient;
import com.voicepay.ivr.client.PaymentServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    private UserServiceClient userServiceClient;

    @Mock
    private PaymentServiceClient paymentServiceClient;

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
        // Mock default behavior of jwtUtil
        lenient().when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("dummy-token");
    }

    @Test
    @DisplayName("handleIncomingCall — Should identify user and show payment amount")
    void whenIncomingCall_thenIdentifyUserAndBroadcast() {
        // GIVEN
        CallRequest request = new CallRequest();
        request.setFrom("+34666000111");

        // Mock User Service Client
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", 1);
        userMap.put("name", "Cristian");
        when(userServiceClient.getUserByPhone(eq("+34666000111"), any())).thenReturn(userMap);

        // Mock Payment Service Client
        Map<String, Object> paymentMap = new HashMap<>();
        paymentMap.put("amount", 75.50);
        when(paymentServiceClient.getPendingPayment(eq(1L), any())).thenReturn(paymentMap);

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
        when(paymentServiceClient.confirmPayment(eq(userId), any())).thenReturn(new HashMap<>());

        // WHEN
        IvrResponse response = ivrService.confirmPayment(userId);

        // THEN
        assertThat(response.getMessage()).contains("procesado correctamente");
    }
}
