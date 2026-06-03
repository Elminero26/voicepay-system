package com.voicepay.notification.controller;

import com.voicepay.notification.model.Notification;
import com.voicepay.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notificaciones", description = "Operaciones para gestionar y enviar notificaciones")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Obtener todas las notificaciones", description = "Devuelve el listado completo de notificaciones registradas (SMS, email, push).")
    public List<Notification> getAllNotifications() {
        return notificationService.getAllNotifications();
    }

    @GetMapping("/recent")
    @Operation(summary = "Obtener notificaciones recientes", description = "Devuelve una lista de las últimas notificaciones enviadas.")
    public List<Notification> getRecentNotifications() {
        return notificationService.getRecentNotifications();
    }

    @PostMapping
    @Operation(summary = "Enviar notificación", description = "Crea y procesa el envío de una nueva notificación (SMS, EMAIL, PUSH).")
    public Notification sendNotification(@RequestBody Notification notification) {
        return notificationService.sendNotification(notification);
    }
}
