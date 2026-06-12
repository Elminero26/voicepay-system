package com.voicepay.ivr.dto;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "call_history")
@Schema(description = "Registro detallado de una llamada en progreso o histórica del bot IVR")
public class LiveCall {
    @Id
    @Schema(description = "Identificador único de la llamada (CallSid de Twilio o UUID simulado)", example = "CAa817b1dcd90b3967406bb1e0506e6bf4")
    private String id;

    @Schema(description = "Número de teléfono involucrado en la llamada en formato E.164", example = "+34600123456")
    private String phoneNumber;

    @Schema(description = "Nombre completo del usuario que recibe/inicia la llamada", example = "Richard Mateo")
    private String userName;

    @Schema(description = "Estado actual de la llamada", example = "CONNECTED", allowableValues = {"CONNECTED", "WAITING_CONFIRMATION", "COMPLETED", "FAILED"})
    private String status; // CONNECTED, WAITING_CONFIRMATION, COMPLETED, FAILED

    @Schema(description = "Importe monetario en EUR asociado a la transacción de la llamada", example = "50.00")
    private double callAmount;

    @Schema(description = "Fecha y hora de inicio de la llamada", example = "2026-06-03T14:30:00")
    private LocalDateTime timestamp;

    @Schema(description = "Duración total de la llamada en segundos", example = "45")
    private Long duration;       // Duración en segundos

    @Schema(description = "Tecla o dígito pulsado por el usuario en el teclado telefónico", example = "1")
    private String selectedOption; // Opción pulsada (ej. "1" para pagar)

    @Column(name = "failed_speech_attempts")
    @Schema(description = "Número de intentos fallidos de entrada de voz consecutivos", example = "0")
    @Builder.Default
    private int failedSpeechAttempts = 0;

    @Schema(description = "Dirección de la llamada", example = "INBOUND", allowableValues = {"INBOUND", "OUTBOUND"})
    private String direction;     // "INBOUND" o "OUTBOUND"

    @Schema(description = "Identificador del miembro de campaña asociado a la llamada", example = "1")
    private Long campaignMemberId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "call_events", joinColumns = @JoinColumn(name = "call_id"))
    @Column(name = "event")
    @Builder.Default
    @Schema(description = "Secuencia de eventos o transcripciones de voz registradas durante la llamada", example = "[\"Llamada iniciada\", \"Usuario pulsó 1\", \"Pago de 50.00 EUR aprobado\"]")
    private java.util.List<String> callEvents = new java.util.ArrayList<>();

    public long getDurationSeconds() {
        return java.time.Duration.between(timestamp, LocalDateTime.now()).getSeconds();
    }
}
