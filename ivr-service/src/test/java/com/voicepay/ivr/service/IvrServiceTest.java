package com.voicepay.ivr.service;

import com.voicepay.ivr.dto.CallRequest;
import com.voicepay.ivr.dto.IvrResponse;
import com.voicepay.ivr.client.UserServiceClient;
import com.voicepay.ivr.client.PaymentServiceClient;
import com.voicepay.ivr.config.TwilioProperties;
import com.voicepay.ivr.nlp.NlpClient;
import com.voicepay.ivr.nlp.NlpResult;
import com.voicepay.ivr.nlp.exception.FallbackIntentException;
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

    @Mock
    private NlpClient nlpClient;

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
        assertThat(twiml).contains("input=\"speech dtmf\"");
        assertThat(twiml).contains("language=\"es-ES\"");
        assertThat(twiml).contains("speechTimeout=\"auto\"");
    }

    @Test
    @DisplayName("handleTwilioWebhook — Should return TwiML Pay when Digit 1 is selected")
    void whenTwilioWebhookDigit1_thenProcessPayment() {
        // GIVEN
        Long userId = 1L;
        String callId = "CA123456789";
        String digits = "1";

        // WHEN
        String twiml = ivrService.handleTwilioWebhook(userId, callId, digits, null, "https://localhost:8082");

        // THEN
        assertThat(twiml).contains("<Pay");
        assertThat(twiml).contains("action=\"https://localhost:8082/ivr/twilio-pay-action?userId=1\"");
        assertThat(twiml).contains("paymentConnector=\"stripe_connector\"");
    }

    @Test
    @DisplayName("handleTwilioWebhook — Should return TwiML Pay when user speaks option 1 ('pagar')")
    void whenTwilioWebhookSpeechPagar_thenProcessPayment() {
        // GIVEN
        Long userId = 1L;
        String callId = "CA123456789";
        String speechResult = "quiero pagar la factura";
        when(nlpClient.analyzeText(speechResult)).thenReturn(new NlpResult("PAY_DEBT", 0.95));

        // WHEN
        String twiml = ivrService.handleTwilioWebhook(userId, callId, null, speechResult, "https://localhost:8082");

        // THEN
        assertThat(twiml).contains("<Pay");
    }

    @Test
    @DisplayName("handleTwilioWebhook — Should transfer call when user speaks option 2 ('agente')")
    void whenTwilioWebhookSpeechAgente_thenTransferToAgent() {
        // GIVEN
        Long userId = 1L;
        String callId = "CA123456789";
        String speechResult = "deseo hablar con un agente";
        when(nlpClient.analyzeText(speechResult)).thenReturn(new NlpResult("TALK_TO_AGENT", 0.95));

        // WHEN
        String twiml = ivrService.handleTwilioWebhook(userId, callId, null, speechResult, "https://localhost:8082");

        // THEN
        assertThat(twiml).contains("transfiriendo");
    }

    @Test
    @DisplayName("handleTwilioWebhook — Should return retry prompt on first speech failure")
    void whenTwilioWebhookSpeechInvalid_thenFail() {
        // GIVEN
        Long userId = 1L;
        String callId = "CA123456789";
        String speechResult = "quiero jugar con mi perro";
        when(nlpClient.analyzeText(speechResult)).thenThrow(new FallbackIntentException("Intent not recognized (FALLBACK)"));

        // WHEN
        String twiml = ivrService.handleTwilioWebhook(userId, callId, null, speechResult, "https://localhost:8082");

        // THEN
        assertThat(twiml).contains("No le hemos entendido");
        assertThat(twiml).contains("input=\"speech dtmf\"");
    }

    @Test
    @DisplayName("handleTwilioWebhook — Should return DTMF-only fallback on second consecutive speech failure")
    void whenTwilioWebhookSpeechInvalidConsecutive_thenSecondRetryDTMF() {
        // GIVEN
        Long userId = 1L;
        String callId = "CA123456789";
        String speechResult = "quiero jugar con mi perro";
        when(nlpClient.analyzeText(speechResult)).thenThrow(new FallbackIntentException("Intent not recognized (FALLBACK)"));

        // WHEN: Attempt 1
        String twiml1 = ivrService.handleTwilioWebhook(userId, callId, null, speechResult, "https://localhost:8082");
        
        // THEN: First attempt should ask to retry with speech and DTMF
        assertThat(twiml1).contains("No le hemos entendido");
        assertThat(twiml1).contains("input=\"speech dtmf\"");

        // WHEN: Attempt 2
        String twiml2 = ivrService.handleTwilioWebhook(userId, callId, null, speechResult, "https://localhost:8082");

        // THEN: Second attempt should ask to use DTMF keypad only
        assertThat(twiml2).contains("No hemos podido entender su voz");
        assertThat(twiml2).contains("input=\"dtmf\"");
    }

    @Test
    @DisplayName("handleTwilioWebhook — Should hang up on third consecutive speech failure")
    void whenTwilioWebhookSpeechInvalidThirdTime_thenHangup() {
        // GIVEN
        Long userId = 1L;
        String callId = "CA123456789";
        String speechResult = "quiero jugar con mi perro";
        when(nlpClient.analyzeText(speechResult)).thenThrow(new FallbackIntentException("Intent not recognized (FALLBACK)"));

        // WHEN: 3 consecutive failures
        ivrService.handleTwilioWebhook(userId, callId, null, speechResult, "https://localhost:8082");
        ivrService.handleTwilioWebhook(userId, callId, null, speechResult, "https://localhost:8082");
        String twiml3 = ivrService.handleTwilioWebhook(userId, callId, null, speechResult, "https://localhost:8082");

        // THEN: Third attempt should say abort message and hang up
        assertThat(twiml3).contains("No hemos recibido una respuesta válida");
        assertThat(twiml3).contains("<Hangup/>");
    }


    @Test
    @DisplayName("handleTwilioWebhook — Should cancel call when user speaks option 3 ('cancelar')")
    void whenTwilioWebhookSpeechCancelar_thenCancelCall() {
        // GIVEN
        Long userId = 1L;
        String callId = "CA123456789";
        String speechResult = "quiero cancelar";
        when(nlpClient.analyzeText(speechResult)).thenReturn(new NlpResult("CANCEL", 0.90));

        // WHEN
        String twiml = ivrService.handleTwilioWebhook(userId, callId, null, speechResult, "https://localhost:8082");

        // THEN
        assertThat(twiml).contains("Operación cancelada");
        assertThat(twiml).contains("<Hangup/>");
    }

    @Test
    @DisplayName("processTwilioPayResult — Should confirm external payment on success result")
    void whenTwilioPayResultSuccess_thenConfirmExternalPayment() {
        // GIVEN
        Long userId = 1L;
        String callSid = "CA123456789";
        String result = "success";
        String paymentStatus = "complete";
        String chargeSid = "ch_12345";

        when(paymentServiceClient.confirmExternalPayment(eq(userId), eq(chargeSid), any())).thenReturn(new HashMap<>());

        // WHEN
        String twiml = ivrService.processTwilioPayResult(userId, callSid, result, paymentStatus, null, chargeSid);

        // THEN
        assertThat(twiml).contains("procesado correctamente");
        assertThat(twiml).contains("<Hangup/>");
        verify(paymentServiceClient, times(1)).confirmExternalPayment(eq(userId), eq(chargeSid), any());
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

    @Test
    @DisplayName("getTwilioAuthToken — Should return token from properties")
    void whenGetTwilioAuthToken_thenReturnToken() {
        when(twilioProperties.getAuthToken()).thenReturn("mock-token");
        String token = ivrService.getTwilioAuthToken();
        assertThat(token).isEqualTo("mock-token");
    }

    @Test
    @DisplayName("processPaymentCallbackAsync — Should invoke confirmExternalPayment and update call status on success")
    void whenProcessPaymentCallback_thenConfirmExternalPayment() throws Exception {
        // GIVEN
        Long userId = 1L;
        String callSid = "CA123456789";
        String token = "tok_12345";

        com.voicepay.ivr.dto.LiveCall mockCall = com.voicepay.ivr.dto.LiveCall.builder()
                .id(callSid)
                .status("WAITING_CONFIRMATION")
                .callEvents(new java.util.ArrayList<>())
                .timestamp(java.time.LocalDateTime.now())
                .build();
        when(callRepository.findById(callSid)).thenReturn(java.util.Optional.of(mockCall));
        when(paymentServiceClient.confirmExternalPayment(eq(userId), eq(token), any())).thenReturn(new java.util.HashMap<>());

        // WHEN
        ivrService.processPaymentCallbackAsync(userId, callSid, token);

        // Wait a bit since it's asynchronous
        Thread.sleep(150);

        // THEN
        verify(paymentServiceClient, times(1)).confirmExternalPayment(eq(userId), eq(token), any());
        verify(callRepository, atLeastOnce()).save(any());
        assertThat(mockCall.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("processPaymentCallbackAsync — Should resolve userId via phone number if not provided")
    void whenProcessPaymentCallbackWithoutUserId_thenResolveAndConfirm() throws Exception {
        // GIVEN
        Long userId = 1L;
        String callSid = "CA123456789";
        String token = "tok_12345";
        String phone = "+34666000111";

        com.voicepay.ivr.dto.LiveCall mockCall = com.voicepay.ivr.dto.LiveCall.builder()
                .id(callSid)
                .phoneNumber(phone)
                .status("WAITING_CONFIRMATION")
                .callEvents(new java.util.ArrayList<>())
                .timestamp(java.time.LocalDateTime.now())
                .build();
        when(callRepository.findById(callSid)).thenReturn(java.util.Optional.of(mockCall));

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", 1);
        userMap.put("name", "Cristian");
        when(userServiceClient.getUserByPhone(eq(phone), any())).thenReturn(userMap);
        when(paymentServiceClient.confirmExternalPayment(eq(userId), eq(token), any())).thenReturn(new java.util.HashMap<>());

        // WHEN
        ivrService.processPaymentCallbackAsync(null, callSid, token);

        // Wait a bit since it's asynchronous
        Thread.sleep(150);

        // THEN
        verify(userServiceClient, times(1)).getUserByPhone(eq(phone), any());
        verify(paymentServiceClient, times(1)).confirmExternalPayment(eq(userId), eq(token), any());
        verify(callRepository, atLeastOnce()).save(any());
        assertThat(mockCall.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("handleTwilioWebhook — Should broadcast partial transcription and return empty TwiML on UnstableSpeechResult")
    void whenTwilioWebhookPartialTranscription_thenBroadcastAndReturnEmptyTwiML() {
        // GIVEN
        Long userId = 1L;
        String callId = "CA123456789";
        String unstableSpeechResult = "quiero pag";

        // WHEN
        String twiml = ivrService.handleTwilioWebhook(userId, callId, null, null, unstableSpeechResult, "https://localhost:8082");

        // THEN
        assertThat(twiml).isEqualTo("<Response/>");
        verify(broadcaster, times(1)).broadcastTranscription(callId, "user", unstableSpeechResult);
    }

    @Test
    @DisplayName("handleTwilioWebhook — Should broadcast final transcription when SpeechResult is present")
    void whenTwilioWebhookSpeechResultPresent_thenBroadcastFinalTranscription() {
        // GIVEN
        Long userId = 1L;
        String callId = "CA123456789";
        String speechResult = "quiero pagar";
        when(nlpClient.analyzeText(speechResult)).thenReturn(new NlpResult("PAY_DEBT", 0.95));

        // WHEN
        ivrService.handleTwilioWebhook(userId, callId, null, speechResult, null, "https://localhost:8082");

        // THEN
        verify(broadcaster, times(1)).broadcastTranscription(callId, "user", speechResult);
    }
}
