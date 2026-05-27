package com.voicepay.payment.controller;

import com.voicepay.payment.model.Subscription;
import com.voicepay.payment.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Suscripciones", description = "Gestión de suscripciones y pagos recurrentes")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping
    @Operation(summary = "Obtener todas las suscripciones", description = "Devuelve el listado completo de suscripciones en el sistema.")
    public List<Subscription> getAllSubscriptions() {
        return subscriptionService.getAllSubscriptions();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener suscripción por ID", description = "Busca y devuelve los detalles de una suscripción específica.")
    public Subscription getSubscriptionById(@PathVariable Long id) {
        return subscriptionService.getSubscriptionById(id);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Suscripciones por usuario", description = "Devuelve las suscripciones de un usuario en particular.")
    public List<Subscription> getSubscriptionsByUser(@PathVariable Long userId) {
        return subscriptionService.getSubscriptionsByUserId(userId);
    }

    @PostMapping
    @Operation(summary = "Crear nueva suscripción", description = "Crea y activa una nueva suscripción periódica para un usuario.")
    public Subscription createSubscription(@Valid @RequestBody Subscription subscription) {
        return subscriptionService.createSubscription(subscription);
    }

    @PostMapping("/cancel/{id}")
    @Operation(summary = "Cancelar suscripción", description = "Cancela una suscripción activa impidiendo cobros futuros.")
    public Subscription cancelSubscription(@PathVariable Long id) {
        return subscriptionService.cancelSubscription(id);
    }

    @PostMapping("/process-due")
    @Operation(summary = "Procesar cobros vencidos manualmente", description = "Ejecuta de manera inmediata la validación y cobro de suscripciones pendientes.")
    public ResponseEntity<String> processDueSubscriptions() {
        subscriptionService.processDueSubscriptions();
        return ResponseEntity.ok("Procesamiento de suscripciones vencidas ejecutado correctamente.");
    }
}
// Fin del controlador de suscripciones
