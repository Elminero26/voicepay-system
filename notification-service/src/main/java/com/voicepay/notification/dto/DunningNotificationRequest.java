package com.voicepay.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Parámetros para el envío de notificaciones de cobro fallido o suspensión de servicios")
public class DunningNotificationRequest {

    @NotBlank(message = "El nombre del cliente es obligatorio")
    @Schema(description = "Nombre completo del cliente", example = "Richard Mateo")
    private String clientName;

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Schema(description = "Correo electrónico del cliente", example = "richard@voicepay.com")
    private String email;

    @NotBlank(message = "El número de teléfono es obligatorio")
    @Schema(description = "Número de teléfono del cliente en formato E.164", example = "+34600123456")
    private String phoneNumber;

    @NotBlank(message = "El nombre de la suscripción es obligatorio")
    @Schema(description = "Nombre o descripción de la suscripción", example = "Plan Premium Mensual")
    private String subscriptionName;

    @NotNull(message = "El monto es obligatorio")
    @Schema(description = "Monto adeudado", example = "29.99")
    private BigDecimal amount;

    @NotBlank(message = "La moneda es obligatoria")
    @Schema(description = "Moneda del cobro", example = "EUR")
    private String currency;

    @NotBlank(message = "El tipo de evento es obligatorio")
    @Schema(description = "Tipo de evento de dunning", example = "WARNING", allowableValues = {"WARNING", "SUSPENSION"})
    private String eventType; // WARNING o SUSPENSION
}
