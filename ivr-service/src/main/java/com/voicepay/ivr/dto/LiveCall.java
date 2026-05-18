package com.voicepay.ivr.dto;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "call_history")
public class LiveCall {
    @Id
    private String id;
    private String phoneNumber;
    private String userName;
    private String status; // CONNECTED, WAITING_CONFIRMATION, COMPLETED, FAILED
    private double callAmount;
    private LocalDateTime timestamp;
    private Long duration;       // Duración en segundos
    private String selectedOption; // Opción pulsada (ej. "1" para pagar)
    private String direction;     // "INBOUND" o "OUTBOUND"

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "call_events", joinColumns = @JoinColumn(name = "call_id"))
    @Column(name = "event")
    @Builder.Default
    private java.util.List<String> callEvents = new java.util.ArrayList<>();

    public long getDurationSeconds() {
        return java.time.Duration.between(timestamp, LocalDateTime.now()).getSeconds();
    }
}
