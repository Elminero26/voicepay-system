package com.voicepay.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpGenerateRequest {

    @NotBlank(message = "El identificador es requerido")
    private String identifier;
    
    private Integer length; // 4 o 6, por defecto 6
    
    private Integer ttlMinutes; // por defecto 3
}
