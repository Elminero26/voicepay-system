package com.voicepay.userservice.controller;

import com.voicepay.userservice.dto.*;
import com.voicepay.userservice.service.OtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/otp")
@RequiredArgsConstructor
@Tag(name = "OTP Engine", description = "Generación y validación de OTPs efímeros")
public class OtpController {

    private final OtpService otpService;

    @PostMapping("/generate")
    @Operation(summary = "Generar un OTP", description = "Genera un código OTP efímero atado a un identificador (usuario o sesión) con expiración configurable.")
    public ResponseEntity<OtpGenerateResponse> generateOtp(@Valid @RequestBody OtpGenerateRequest request) {
        int length = request.getLength() != null ? request.getLength() : 6;
        int ttlMinutes = request.getTtlMinutes() != null ? request.getTtlMinutes() : 3;
        
        OtpGenerateResponse response = otpService.generateOtp(request.getIdentifier(), length, ttlMinutes);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/validate")
    @Operation(summary = "Validar un OTP", description = "Valida un código OTP. Si es exitoso, se invalida de inmediato.")
    public ResponseEntity<OtpValidateResponse> validateOtp(@Valid @RequestBody OtpValidateRequest request) {
        OtpValidateResponse response = otpService.validateOtp(request.getIdentifier(), request.getCode());
        return ResponseEntity.ok(response);
    }
}
