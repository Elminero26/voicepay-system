package com.voicepay.userservice.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Table(name = "campaign_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Entidad que representa a un miembro de una campaña en el sistema VoicePay")
public class CampaignMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "Identificador único incremental del miembro de campaña", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    @Schema(description = "Campaña a la que pertenece el miembro")
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @Schema(description = "Usuario (contacto) asociado al miembro de campaña")
    private User user;

    @NotBlank(message = "Phone number is required")
    @Column(nullable = false)
    @Schema(description = "Número de teléfono en formato E.164", example = "+34600123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String phoneNumber;

    @NotNull(message = "Associated debt is required")
    @Column(nullable = false)
    @Schema(description = "Deuda asociada al contacto", example = "150.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double associatedDebt;

    @NotBlank(message = "Call status is required")
    @Column(nullable = false)
    @Schema(description = "Estado de llamada (PENDING, RINGING, COMPLETED, NO_ANSWER, BUSY)", example = "PENDING", requiredMode = Schema.RequiredMode.REQUIRED)
    private String callStatus; // PENDING, RINGING, COMPLETED, NO_ANSWER, BUSY
}
