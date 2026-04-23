package com.voicepay.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Random;

@Slf4j
@Component
public class PaymentGatewaySimulator {

    private final Random random = new Random();

    /**
     * Simula el procesamiento de un pago a través de una pasarela externa (Stripe, PayPal, etc.)
     * @param amount Monto a procesar
     * @return true si el pago fue exitoso, false en caso contrario
     */
    public boolean processPayment(BigDecimal amount) {
        log.info("Iniciando comunicación con la pasarela de pago para un monto de: {} EUR", amount);
        
        try {
            // Simulamos latencia de red (500ms - 1500ms)
            long latency = 500 + random.nextInt(1000);
            Thread.sleep(latency);
            
            // Simulamos una tasa de éxito del 90%
            // Si el monto es exactamente 100.0, simulamos un fallo para pruebas
            if (amount.compareTo(new BigDecimal("100.0")) == 0) {
                log.warn("Pago rechazado por la pasarela (Simulación de fallo para monto 100.0)");
                return false;
            }
            
            boolean success = random.nextDouble() < 0.9;
            
            if (success) {
                log.info("Pago aprobado por la pasarela tras {}ms", latency);
            } else {
                log.warn("Pago rechazado por la pasarela tras {}ms (Fallo aleatorio)", latency);
            }
            
            return success;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("La comunicación con la pasarela fue interrumpida", e);
            return false;
        }
    }
}
