package com.voicepay.ivr.controller;

import com.voicepay.ivr.dto.CallRequest;
import com.voicepay.ivr.dto.IvrResponse;
import com.voicepay.ivr.service.IvrService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class IvrControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
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
}
