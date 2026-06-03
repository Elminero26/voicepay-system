package com.voicepay.ivr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Respuesta del bot de voz IVR con la acción a seguir")
public class IvrResponse {
    @Schema(description = "Mensaje hablado que el bot de voz emitirá al usuario", example = "Bienvenido a VoicePay. Pulse 1 para realizar su pago pendiente de 50 EUR.")
    private String message;

    @Schema(description = "Siguiente acción del flujo Twilio (ej. 'gather', 'hangup', 'play')", example = "gather")
    private String nextAction;

    @Schema(description = "Identificador único del usuario asociado al teléfono de la llamada", example = "1")
    private Long userId;
}
