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
public class OtpValidateRequest {

    @NotBlank(message = "El identificador es requerido")
    private String identifier;
    
    @NotBlank(message = "El código OTP es requerido")
    private String code;
}
