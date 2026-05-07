package com.voicepay.payment.service;

import com.voicepay.payment.dto.PaymentStats;
import com.voicepay.payment.model.Payment;
import com.voicepay.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RestTemplate restTemplate;
    private final PaymentGatewaySimulator paymentGatewaySimulator;

    @Value("${app.user-service.url}")
    private String userServiceUrl;

    @Value("${app.api.key}")
    private String apiKey;

    @Value("${app.notification-service.url}")
    private String notificationServiceUrl;

    private org.springframework.http.HttpHeaders getHeadersWithApiKey() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("X-API-KEY", apiKey);
        return headers;
    }

    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    public List<Payment> getPaymentsByUserId(Long userId) {
        return paymentRepository.findByUserId(userId);
    }

    public List<Payment> getRecentPayments() {
        return paymentRepository.findTop10ByOrderByCreatedAtDesc();
    }

    public PaymentStats getPaymentStats() {
        long completed = paymentRepository.countByStatus(Payment.PaymentStatus.COMPLETED);
        long failed = paymentRepository.countByStatus(Payment.PaymentStatus.FAILED);
        long pending = paymentRepository.countByStatus(Payment.PaymentStatus.PENDING);
        
        BigDecimal totalAmount = paymentRepository.findAll().stream()
                .filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PaymentStats.builder()
                .completed(completed)
                .failed(failed)
                .pending(pending)
                .totalAmount(totalAmount)
                .build();
    }

    public Payment createPayment(Payment payment) {
        // Validación: Consultamos al user-service si el usuario existe con API Key
        try {
            org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(getHeadersWithApiKey());
            restTemplate.exchange(
                    userServiceUrl + "/" + payment.getUserId(),
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    Object.class);
        } catch (Exception e) {
            throw new RuntimeException("Validation failed: User does not exist or User Service is down.");
        }

        // Simplemente guardamos el pago como PENDING para que el IVR lo gestione
        payment.setStatus(Payment.PaymentStatus.PENDING);
        return paymentRepository.save(payment);
    }

    public Payment completePaymentByUserId(Long userId) {
        // Buscamos el último pago pendiente de este usuario
        List<Payment> payments = paymentRepository.findByUserId(userId);
        Payment pendingPayment = payments.stream()
                .filter(p -> p.getStatus() == Payment.PaymentStatus.PENDING)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No pending payment found for user: " + userId));

        // Llamamos al simulador de la pasarela
        boolean success = paymentGatewaySimulator.processPayment(pendingPayment.getAmount());

        if (success) {
            pendingPayment.setStatus(Payment.PaymentStatus.COMPLETED);
            pendingPayment.setTransactionId("TX-" + System.currentTimeMillis()); // 🆔 Generamos el ID aquí
            sendNotification(pendingPayment, "¡Pago completado! Se han cargado " + pendingPayment.getAmount() + " " + pendingPayment.getCurrency() + " a su cuenta.");
        } else {
            pendingPayment.setStatus(Payment.PaymentStatus.FAILED);
            pendingPayment.setTransactionId("TX-FAILED-" + System.currentTimeMillis());
            sendNotification(pendingPayment, "Error en el pago: Su transacción no ha podido ser procesada.");
        }

        return paymentRepository.save(pendingPayment);
    }

    private void sendNotification(Payment payment, String message) {
        try {
            // 👤 Paso extra: Intentamos obtener el nombre del usuario desde el User Service
            String userName = "Usuario";
            try {
                org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(getHeadersWithApiKey());
                org.springframework.http.ResponseEntity<java.util.Map> userResponse = restTemplate.exchange(
                        userServiceUrl + "/" + payment.getUserId(),
                        org.springframework.http.HttpMethod.GET,
                        entity,
                        java.util.Map.class);
                
                if (userResponse.getBody() != null && userResponse.getBody().get("name") != null) {
                    userName = userResponse.getBody().get("name").toString();
                }
            } catch (Exception e) {
                System.err.println("No se pudo obtener el nombre del usuario: " + e.getMessage());
            }

            org.springframework.http.HttpHeaders headers = getHeadersWithApiKey();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

            java.util.Map<String, String> notificationRequest = new java.util.HashMap<>();
            notificationRequest.put("recipient", userName); // Ahora usamos el NOMBRE real
            notificationRequest.put("message", "Hola " + userName + ", " + message);
            notificationRequest.put("type", "PUSH");

            org.springframework.http.HttpEntity<java.util.Map<String, String>> request = 
                new org.springframework.http.HttpEntity<>(notificationRequest, headers);
            
            restTemplate.postForEntity(notificationServiceUrl, request, String.class);
        } catch (Exception e) {
            System.err.println("Error enviando notificación: " + e.getMessage());
        }
    }

    public Payment getPaymentById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + id));
    }
}
