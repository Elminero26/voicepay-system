package com.voicepay.payment.service;

import com.voicepay.payment.dto.PaymentStats;
import com.voicepay.payment.model.Payment;
import com.voicepay.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.voicepay.payment.client.UserServiceClient;
import com.voicepay.payment.client.NotificationServiceClient;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGatewaySimulator paymentGatewaySimulator;
    private final UserServiceClient userServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final CurrencyExchangeService currencyExchangeService;

    private final com.voicepay.payment.security.JwtUtil jwtUtil;

    private org.springframework.http.HttpHeaders getHeadersWithJwt() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        String token = jwtUtil.generateToken("payment-service", "ROLE_ADMIN");
        headers.set("Authorization", "Bearer " + token);
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
                .map(p -> p.getConvertedAmount() != null ? p.getConvertedAmount() : p.getAmount())
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
            userServiceClient.validateUser(payment.getUserId(), getHeadersWithJwt());
        } catch (Exception e) {
            throw new RuntimeException("Validation failed: User does not exist or User Service is down.");
        }

        // Calcular tasa de cambio y monto convertido a la divisa base (EUR)
        BigDecimal rate = currencyExchangeService.getRate(payment.getCurrency());
        BigDecimal convertedAmount = currencyExchangeService.convert(payment.getAmount(), payment.getCurrency(), "EUR");
        payment.setExchangeRate(rate);
        payment.setConvertedAmount(convertedAmount);

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

        // Llamamos al simulador de la pasarela indicando la divisa correspondiente
        boolean success = paymentGatewaySimulator.processPayment(pendingPayment.getAmount(), pendingPayment.getCurrency());

        if (success) {
            pendingPayment.setStatus(Payment.PaymentStatus.COMPLETED);
            pendingPayment.setTransactionId("TX-" + System.currentTimeMillis()); // 🆔 Generamos el ID aquí
            
            String notifMsg = "¡Pago completado! Se han cargado " + pendingPayment.getAmount() + " " + pendingPayment.getCurrency() + " a su cuenta.";
            if (!"EUR".equalsIgnoreCase(pendingPayment.getCurrency()) && pendingPayment.getConvertedAmount() != null) {
                notifMsg += " (Equivalente a " + pendingPayment.getConvertedAmount() + " EUR, Tasa: " + pendingPayment.getExchangeRate() + ")";
            }
            sendNotification(pendingPayment, notifMsg);
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
                Map<String, Object> userResponse = userServiceClient.getUserDetails(payment.getUserId(), getHeadersWithJwt());
                if (userResponse != null && userResponse.get("name") != null) {
                    userName = userResponse.get("name").toString();
                }
            } catch (Exception e) {
                System.err.println("No se pudo obtener el nombre del usuario: " + e.getMessage());
            }

            org.springframework.http.HttpHeaders headers = getHeadersWithJwt();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

            java.util.Map<String, String> notificationRequest = new java.util.HashMap<>();
            notificationRequest.put("recipient", userName); // Ahora usamos el NOMBRE real
            notificationRequest.put("message", "Hola " + userName + ", " + message);
            notificationRequest.put("type", "PUSH");

            notificationServiceClient.sendNotification(notificationRequest, headers);
        } catch (Exception e) {
            System.err.println("Error enviando notificación: " + e.getMessage());
        }
    }

    public Payment completeExternalPayment(Long userId, String chargeSid) {
        List<Payment> payments = paymentRepository.findByUserId(userId);
        Payment pendingPayment = payments.stream()
                .filter(p -> p.getStatus() == Payment.PaymentStatus.PENDING)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No pending payment found for user: " + userId));

        pendingPayment.setStatus(Payment.PaymentStatus.COMPLETED);
        pendingPayment.setTransactionId(chargeSid);

        String notifMsg = "¡Pago completado! Se han cargado " + pendingPayment.getAmount() + " " + pendingPayment.getCurrency() + " a su cuenta.";
        if (!"EUR".equalsIgnoreCase(pendingPayment.getCurrency()) && pendingPayment.getConvertedAmount() != null) {
            notifMsg += " (Equivalente a " + pendingPayment.getConvertedAmount() + " EUR, Tasa: " + pendingPayment.getExchangeRate() + ")";
        }
        sendNotification(pendingPayment, notifMsg);

        return paymentRepository.save(pendingPayment);
    }

    public Payment getPaymentById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + id));
    }
}
