package com.voicepay.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Datos para iniciar sesión")
public class LoginRequest {
    @Schema(description = "Correo electrónico del usuario registrado", example = "admin@voicepay.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Schema(description = "Contraseña en texto plano del usuario", example = "admin123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
