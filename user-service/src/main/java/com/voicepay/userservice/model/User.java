package com.voicepay.userservice.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import com.voicepay.userservice.security.DeterministicEncryptionConverter;
import lombok.*;

import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Entidad que representa a un usuario en el sistema VoicePay")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "Identificador único incremental del usuario", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @NotBlank(message = "Name is required")
    @Column(nullable = false)
    @Schema(description = "Nombre completo del usuario", example = "Richard Mateo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Column(nullable = false, unique = true)
    @Schema(description = "Correo electrónico único y válido", example = "richard@voicepay.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @Convert(converter = DeterministicEncryptionConverter.class)
    @Column(unique = true)
    @Schema(description = "Número de teléfono en formato E.164 (encriptado determinísticamente en base de datos)", example = "+34600123456")
    private String phoneNumber;

    @Column(nullable = true)
    @Schema(description = "Contraseña en texto plano para el registro (se guarda encriptada y no se retorna en las lecturas)", example = "richard123", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;

    @Column(nullable = false)
    @Builder.Default
    @Schema(description = "Rol de seguridad asignado al usuario", example = "ROLE_USER")
    private String role = "ROLE_USER";

    @Column(nullable = false)
    @Builder.Default
    @Schema(description = "Indica si el usuario está activo en el sistema", example = "true")
    private Boolean active = true;

    @Schema(description = "Proveedor de autenticación (local, google, microsoft)", example = "local")
    private String provider;

    @Schema(description = "ID del usuario dentro del proveedor de identidad externo", example = "1048572918573")
    private String providerId;
}
