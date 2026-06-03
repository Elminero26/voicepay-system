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
@Schema(description = "Respuesta de autenticación exitosa")
public class AuthResponse {
    @Schema(description = "Token de acceso JWT (válido por 1 hora)", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token;

    @Schema(description = "Token de refresco para obtener nuevos access tokens (válido por 7 días)", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String refreshToken;

    @Schema(description = "Correo electrónico del usuario autenticado", example = "admin@voicepay.com")
    private String email;

    @Schema(description = "Rol del usuario en el sistema", example = "ROLE_ADMIN")
    private String role;
}
