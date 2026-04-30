package com.voicepay.userservice.controller;

import com.voicepay.userservice.dto.AuthResponse;
import com.voicepay.userservice.dto.LoginRequest;
import com.voicepay.userservice.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Endpoints para Login y generación de tokens JWT")
public class AuthController {

    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión", description = "Valida las credenciales y devuelve un token JWT.")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        // Hardcoded admin validation for simplicity. In production, this would verify against DB hash.
        if ("admin@voicepay.com".equals(loginRequest.getEmail()) && "admin123".equals(loginRequest.getPassword())) {
            String token = jwtUtil.generateToken(loginRequest.getEmail());
            return ResponseEntity.ok(new AuthResponse(token));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Credenciales incorrectas");
    }
}
