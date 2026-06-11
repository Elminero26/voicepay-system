package com.voicepay.ivr.controller;

import com.voicepay.ivr.dto.CallRequest;
import com.voicepay.ivr.dto.IvrResponse;
import com.voicepay.ivr.service.IvrService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.defer-datasource-initialization=true",
    "spring.sql.init.mode=never",
    "app.user-service.url=http://localhost:8080/users",
    "app.payment-service.url=http://localhost:8081/payments",
    "twilio.account-sid=AC_DUMMY_SID_FOR_TESTS",
    "twilio.auth-token=DUMMY_TOKEN_FOR_TESTS",
    "twilio.phone-number=+10000000000",
    "twilio.webhook-url=http://localhost:8082"
})
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings("null")
public class IvrControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IvrService ivrService;

    @Test
    void handleCall_ShouldReturnWelcomeMessage() throws Exception {
        IvrResponse mockResponse = IvrResponse.builder()
                .message("Bienvenido Mateo. Usted tiene un pago pendiente.")
                .nextAction("WAIT_FOR_CONFIRMATION")
                .userId(3L)
                .build();

        when(ivrService.handleIncomingCall(any(CallRequest.class))).thenReturn(mockResponse);

        CallRequest request = new CallRequest();
        request.setFrom("+34777777777");

        mockMvc.perform(post("/ivr/call")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Bienvenido Mateo. Usted tiene un pago pendiente."))
                .andExpect(jsonPath("$.userId").value(3));
    }

    @Test
    void handleTwilioCall_ShouldReturnTwiML() throws Exception {
        String mockTwiML = "<Response><Say>Hola Mateo</Say></Response>";
        when(ivrService.handleTwilioCall(anyString(), anyString())).thenReturn(mockTwiML);

        mockMvc.perform(post("/ivr/twilio-call")
                .param("From", "+34777777777")
                .param("CallSid", "CA123456789"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(mockTwiML));
    }

    @Test
    void handleTwilioWebhook_ShouldReturnTwiMLResponse() throws Exception {
        String mockTwiML = "<Response><Say>Pago confirmado</Say></Response>";
        when(ivrService.handleTwilioWebhook(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(mockTwiML);

        mockMvc.perform(post("/ivr/twilio-webhook")
                .param("userId", "3")
                .param("callId", "CA123456789")
                .param("Digits", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(mockTwiML));
    }

    @Test
    void handleTwilioStatus_ShouldReturnOk() throws Exception {
        doNothing().when(ivrService).handleTwilioStatus(anyString(), anyString(), anyString());

        mockMvc.perform(post("/ivr/twilio-status")
                .param("CallSid", "CA123456789")
                .param("CallStatus", "completed")
                .param("CallDuration", "12"))
                .andExpect(status().isOk());
    }

    @Test
    void handlePaymentCallback_ShouldReturnTwiML() throws Exception {
        when(ivrService.getTwilioAuthToken()).thenReturn("DUMMY_TOKEN");
        doNothing().when(ivrService).processPaymentCallbackAsync(anyLong(), anyString(), anyString());

        mockMvc.perform(post("/ivr/payment-callback")
                .param("userId", "3")
                .param("CallSid", "CA123456789")
                .param("PaymentToken", "tok_12345"))
                .andExpect(status().isOk())
                .andExpect(content().string("<Response/>"));
    }

    @Test
    void triggerOutboundCall_ShouldReturnResponse() throws Exception {
        IvrResponse mockResponse = IvrResponse.builder()
                .message("Llamada saliente iniciada.")
                .nextAction("WAIT_FOR_INPUT")
                .userId(3L)
                .build();

        when(ivrService.triggerOutboundCall(anyString(), anyBoolean())).thenReturn(mockResponse);

        CallRequest request = new CallRequest();
        request.setFrom("+34777777777");

        mockMvc.perform(post("/ivr/outbound")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .param("mock", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Llamada saliente iniciada."));
    }
}
