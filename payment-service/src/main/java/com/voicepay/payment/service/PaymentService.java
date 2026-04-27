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
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RestTemplate restTemplate;
    private final PaymentGatewaySimulator paymentGatewaySimulator;

    @Value("${app.user-service.url}")
    private String userServiceUrl;

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
        // Validación: Consultamos al user-service si el usuario existe
        try {
            restTemplate.getForObject(userServiceUrl + "/" + payment.getUserId(), Object.class);
        } catch (Exception e) {
            throw new RuntimeException("Validation failed: User does not exist or User Service is down.");
        }

        // Primero guardamos el pago como PENDING
        payment.setStatus(Payment.PaymentStatus.PENDING);
        Payment savedPayment = paymentRepository.save(payment);

        // Llamamos al simulador de la pasarela
        boolean success = paymentGatewaySimulator.processPayment(savedPayment.getAmount());

        if (success) {
            savedPayment.setStatus(Payment.PaymentStatus.COMPLETED);
        } else {
            savedPayment.setStatus(Payment.PaymentStatus.FAILED);
        }

        return paymentRepository.save(savedPayment);
    }

    public Payment getPaymentById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + id));
    }
}
