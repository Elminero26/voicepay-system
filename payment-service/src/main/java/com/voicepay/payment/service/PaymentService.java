package com.voicepay.payment.service;

import com.voicepay.payment.model.Payment;
import com.voicepay.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RestTemplate restTemplate;

    @Value("${app.user-service.url}")
    private String userServiceUrl;

    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    public List<Payment> getPaymentsByUserId(Long userId) {
        return paymentRepository.findByUserId(userId);
    }

    public Payment createPayment(Payment payment) {
        // Validación: Consultamos al user-service si el usuario existe
        try {
            restTemplate.getForObject(userServiceUrl + "/" + payment.getUserId(), Object.class);
        } catch (Exception e) {
            throw new RuntimeException("Validation failed: User does not exist or User Service is down.");
        }

        // Por ahora lo marcamos como COMPLETED por defecto
        payment.setStatus(Payment.PaymentStatus.COMPLETED);
        return paymentRepository.save(payment);
    }

    public Payment getPaymentById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + id));
    }
}
