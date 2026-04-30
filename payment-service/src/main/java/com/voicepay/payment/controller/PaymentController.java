package com.voicepay.payment.controller;

import com.voicepay.payment.dto.PaymentStats;
import com.voicepay.payment.model.Payment;
import com.voicepay.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Tag(name = "Pagos", description = "Gestión y estadísticas de pagos")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    @Operation(summary = "Obtener todos los pagos", description = "Devuelve el historial completo de pagos registrados.")
    public List<Payment> getAllPayments() {
        return paymentService.getAllPayments();
    }

    @PostMapping
    @Operation(summary = "Crear nuevo pago", description = "Registra un nuevo pago en el sistema.")
    public Payment createPayment(@Valid @RequestBody Payment payment) {
        return paymentService.createPayment(payment);
    }

    @GetMapping("/stats")
    @Operation(summary = "Obtener estadísticas", description = "Devuelve estadísticas resumidas (total cobrado, pagos fallidos, etc.).")
    public PaymentStats getStats() {
        return paymentService.getPaymentStats();
    }

    @GetMapping("/recent")
    @Operation(summary = "Pagos recientes", description = "Devuelve los últimos pagos realizados ordenados por fecha.")
    public List<Payment> getRecentPayments() {
        return paymentService.getRecentPayments();
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Pagos por usuario", description = "Devuelve el historial de pagos de un usuario en particular.")
    public List<Payment> getPaymentsByUser(@PathVariable Long userId) {
        return paymentService.getPaymentsByUserId(userId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener pago por ID", description = "Busca y devuelve los detalles de un pago específico.")
    public Payment getPaymentById(@PathVariable Long id) {
        return paymentService.getPaymentById(id);
    }
}
