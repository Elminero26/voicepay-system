package com.voicepay.ivr.service;

import com.voicepay.ivr.dto.CallRequest;
import com.voicepay.ivr.dto.IvrResponse;
import com.voicepay.ivr.client.UserServiceClient;
import com.voicepay.ivr.client.PaymentServiceClient;
import com.voicepay.ivr.config.TwilioProperties;
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
@SuppressWarnings("null")
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

    @Mock
    private TwilioProperties twilioProperties;

    @InjectMocks
    private IvrService ivrService;

    @BeforeEach
    void setUp() {
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
        
        verify(broadcaster, times(1)).broadcast(anyCollection());
    }

    @Test
    @DisplayName("confirmPayment — Should update status to COMPLETED")
    void whenConfirmingPayment_thenStatusIsCompleted() {
        // GIVEN
        Long userId = 1L;
        when(paymentServiceClient.confirmPayment(eq(userId), any())).thenReturn(new HashMap<>());

        // WHEN
        IvrResponse response = ivrService.confirmPayment(userId);

        // THEN
        assertThat(response.getMessage()).contains("procesado correctamente");
    }

    @Test
    @DisplayName("handleTwilioCall — Should return valid Spanish TwiML including welcome prompts")
    void whenTwilioCallReceived_thenReturnValidTwiML() {
        // GIVEN
        String from = "+34666000111";
        String callSid = "CA123456789";

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", 1);
        userMap.put("name", "Cristian");
        when(userServiceClient.getUserByPhone(eq(from), any())).thenReturn(userMap);

        Map<String, Object> paymentMap = new HashMap<>();
        paymentMap.put("amount", 25.00);
        when(paymentServiceClient.getPendingPayment(eq(1L), any())).thenReturn(paymentMap);

        // WHEN
        String twiml = ivrService.handleTwilioCall(from, callSid);

        // THEN
        assertThat(twiml).contains("<Response>");
        assertThat(twiml).contains("Hola Cristian");
        assertThat(twiml).contains("/ivr/twilio-webhook");
    }

    @Test
    @DisplayName("handleTwilioWebhook — Should trigger confirmPayment when Digit 1 is selected")
    void whenTwilioWebhookDigit1_thenProcessPayment() {
        // GIVEN
        Long userId = 1L;
        String callId = "CA123456789";
        String digits = "1";

        when(paymentServiceClient.confirmPayment(eq(userId), any())).thenReturn(new HashMap<>());

        // WHEN
        String twiml = ivrService.handleTwilioWebhook(userId, callId, digits);

        // THEN
        assertThat(twiml).contains("procesado correctamente");
        assertThat(twiml).contains("<Hangup/>");
    }

    @Test
    @DisplayName("handleTwilioStatus — Should capture completed status callbacks")
    void whenTwilioStatusCallbackReceived_thenProcessState() {
        // GIVEN
        String callSid = "CA123456789";
        String status = "completed";
        String duration = "15";

        com.voicepay.ivr.dto.LiveCall mockCall = com.voicepay.ivr.dto.LiveCall.builder()
                .id(callSid)
                .status("WAITING_CONFIRMATION")
                .callEvents(new java.util.ArrayList<>())
                .build();
        when(callRepository.findById(callSid)).thenReturn(java.util.Optional.of(mockCall));

        // WHEN
        ivrService.handleTwilioStatus(callSid, status, duration);

        // THEN
        verify(callRepository, atLeastOnce()).save(any());
    }

    @Test
    @DisplayName("triggerOutboundCall — Should initialize mock visual flow successfully")
    void whenOutboundCallMockRequested_thenStartSimulation() {
        // GIVEN
        String to = "+34666000111";
        boolean forceMock = true;

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", 1);
        userMap.put("name", "Cristian");
        when(userServiceClient.getUserByPhone(eq(to), any())).thenReturn(userMap);

        // WHEN
        IvrResponse response = ivrService.triggerOutboundCall(to, forceMock);

        // THEN
        assertThat(response.getMessage()).contains("iniciada");
        assertThat(response.getNextAction()).isEqualTo("SIMULATION_RUNNING");
    }
}
