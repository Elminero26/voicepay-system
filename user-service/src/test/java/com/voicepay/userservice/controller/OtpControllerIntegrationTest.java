package com.voicepay.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicepay.userservice.dto.*;
import com.voicepay.userservice.service.OtpService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:otp_ctrl_testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.defer-datasource-initialization=true",
    "spring.sql.init.mode=never",
    "spring.flyway.enabled=false",
    "spring.security.oauth2.client.registration.google.client-id=dummy-id",
    "spring.security.oauth2.client.registration.google.client-secret=dummy-secret",
    "spring.security.oauth2.client.registration.google.scope=profile,email"
})
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings("null")
public class OtpControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OtpService otpService;

    @Test
    void generateOtp_ShouldReturnOtpResponse() throws Exception {
        OtpGenerateRequest request = OtpGenerateRequest.builder()
                .identifier("phone-test")
                .length(6)
                .ttlMinutes(3)
                .build();

        OtpGenerateResponse mockResponse = OtpGenerateResponse.builder()
                .identifier("phone-test")
                .code("123456")
                .expiryTime(LocalDateTime.now().plusMinutes(3))
                .build();

        when(otpService.generateOtp(eq("phone-test"), eq(6), eq(3))).thenReturn(mockResponse);

        mockMvc.perform(post("/auth/otp/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identifier").value("phone-test"))
                .andExpect(jsonPath("$.code").value("123456"));
    }

    @Test
    void validateOtp_ShouldReturnValidationResponse() throws Exception {
        OtpValidateRequest request = OtpValidateRequest.builder()
                .identifier("phone-test")
                .code("123456")
                .build();

        OtpValidateResponse mockResponse = OtpValidateResponse.builder()
                .valid(true)
                .message("OTP validado con éxito")
                .build();

        when(otpService.validateOtp(eq("phone-test"), eq("123456"))).thenReturn(mockResponse);

        mockMvc.perform(post("/auth/otp/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.message").value("OTP validado con éxito"));
    }
}
