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
    private final com.voicepay.payment.service.CurrencyExchangeService currencyExchangeService;

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

    @GetMapping("/pending/{userId}")
    @Operation(summary = "Pago pendiente por usuario", description = "Devuelve el último pago pendiente de un usuario específico.")
    public Payment getPendingPayment(@PathVariable Long userId) {
        return paymentService.getPaymentsByUserId(userId).stream()
                .filter(p -> p.getStatus() == Payment.PaymentStatus.PENDING)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No pending payment found for user: " + userId));
    }

    @PostMapping("/confirm/{userId}")
    @Operation(summary = "Confirmar pago", description = "Procesa y completa el último pago pendiente de un usuario.")
    public Payment confirmPayment(@PathVariable Long userId) {
        return paymentService.completePaymentByUserId(userId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener pago por ID", description = "Busca y devuelve los detalles de un pago específico.")
    public Payment getPaymentById(@PathVariable Long id) {
        return paymentService.getPaymentById(id);
    }

    @GetMapping("/exchange-rates")
    @Operation(summary = "Obtener tipos de cambio", description = "Devuelve los tipos de cambio en tiempo real respecto al EUR y la última fecha de actualización.")
    public java.util.Map<String, Object> getExchangeRates() {
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("base", "EUR");
        response.put("rates", currencyExchangeService.getExchangeRates());
        response.put("lastUpdated", currencyExchangeService.getLastUpdated());
        return response;
    }

    @GetMapping("/exchange-rates/convert")
    @Operation(summary = "Convertir importe", description = "Convierte un importe entre dos divisas gestionando la precisión monetaria.")
    public java.util.Map<String, Object> convertCurrency(
            @RequestParam java.math.BigDecimal amount,
            @RequestParam String from,
            @RequestParam String to) {
        java.math.BigDecimal converted = currencyExchangeService.convert(amount, from, to);
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("amount", amount);
        response.put("from", from.toUpperCase());
        response.put("to", to.toUpperCase());
        response.put("convertedAmount", converted);
        response.put("rate", currencyExchangeService.getRate(from).compareTo(java.math.BigDecimal.ZERO) > 0 
                ? currencyExchangeService.getRate(to).divide(currencyExchangeService.getRate(from), 4, java.math.RoundingMode.HALF_UP) 
                : java.math.BigDecimal.ONE);
        return response;
    }

    @PostMapping("/exchange-rates/update")
    @Operation(summary = "Actualizar tipos de cambio", description = "Fuerza la actualización manual de los tipos de cambio de divisas en tiempo real desde la API FX.")
    public java.util.Map<String, Object> updateExchangeRates() {
        currencyExchangeService.updateExchangeRates();
        return getExchangeRates();
    }
}
