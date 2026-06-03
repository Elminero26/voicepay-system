package com.voicepay.ivr.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Solicitud para simular o iniciar una llamada IVR")
public class CallRequest {
    @NotBlank(message = "Phone number is required")
    @Schema(description = "Número de teléfono origen de la llamada en formato E.164", example = "+34600123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String from;
}
