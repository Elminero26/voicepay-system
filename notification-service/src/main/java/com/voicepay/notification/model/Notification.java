package com.voicepay.notification.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Entidad que representa una notificación enviada en el sistema VoicePay")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "Identificador único incremental de la notificación", example = "1", readOnly = true)
    private Long id;

    @Schema(description = "Destinatario de la notificación (correo electrónico o número de teléfono)", example = "+34600123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String recipient; // Phone number or email

    @Schema(description = "Mensaje o cuerpo del contenido de la notificación", example = "Su pago de 50.00 EUR ha sido procesado con éxito.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;
    
    @Enumerated(EnumType.STRING)
    @Schema(description = "Canal de envío de la notificación", example = "SMS", allowableValues = {"SMS", "EMAIL", "PUSH"})
    private NotificationType type; // SMS, EMAIL, PUSH

    @Enumerated(EnumType.STRING)
    @Schema(description = "Estado de entrega de la notificación", example = "SENT", allowableValues = {"PENDING", "SENT", "FAILED"})
    private NotificationStatus status; // PENDING, SENT, FAILED

    @Schema(description = "Fecha y hora de creación del registro de notificación", example = "2026-06-03T14:30:00")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = NotificationStatus.PENDING;
    }

    public enum NotificationType {
        SMS, EMAIL, PUSH
    }

    public enum NotificationStatus {
        PENDING, SENT, FAILED
    }
}
