package com.voicepay.userservice.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Entity
@Table(name = "campaigns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Entidad que representa una campaña en el sistema VoicePay")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "Identificador único incremental de la campaña", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @NotBlank(message = "Campaign name is required")
    @Column(nullable = false)
    @Schema(description = "Nombre de la campaña", example = "Campaña de Cobro Junio", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotNull(message = "Start date is required")
    @Column(nullable = false)
    @Schema(description = "Fecha de inicio de la campaña", example = "2026-06-11T12:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime startDate;

    @NotBlank(message = "Status is required")
    @Column(nullable = false)
    @Schema(description = "Estado actual de la campaña (DRAFT, ACTIVE, COMPLETED, etc.)", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;

    @NotNull(message = "Max retries is required")
    @Column(nullable = false)
    @Schema(description = "Número máximo de reintentos de llamada", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer maxRetries;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commerce_id", nullable = false)
    @Schema(description = "Comercio asociado a la campaña")
    private Commerce commerce;
}
