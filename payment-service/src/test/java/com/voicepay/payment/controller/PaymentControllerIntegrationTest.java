package com.voicepay.payment.controller;

import com.voicepay.payment.model.Payment;
import com.voicepay.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@SuppressWarnings("null")
public class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void confirmPayment_ShouldReturnOk() throws Exception {
        // Configuración: Simulamos que el servicio devuelve un pago completado
        Payment mockPayment = new Payment();
        mockPayment.setId(1L);
        mockPayment.setUserId(3L);
        mockPayment.setAmount(new BigDecimal("50.0"));
        mockPayment.setStatus(Payment.PaymentStatus.COMPLETED);

        when(paymentService.completePaymentByUserId(3L)).thenReturn(mockPayment);

        // EJECUCIÓN: Simulamos el POST HTTP /payments/confirm/3
        mockMvc.perform(post("/payments/confirm/3")
                .contentType(MediaType.APPLICATION_JSON))
                // VERIFICACIÓN
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.userId").value(3));
    }
}
