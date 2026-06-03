package com.voicepay.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Datos para renovación de token de acceso")
public class RefreshTokenRequest {
    @Schema(description = "Token de refresco JWT previamente generado", example = "eyJhbGciOiJIUzI1NiIsIn...", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;
}
