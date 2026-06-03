package com.voicepay.userservice.controller;

import com.voicepay.userservice.dto.AuthResponse;
import com.voicepay.userservice.dto.LoginRequest;
import com.voicepay.userservice.dto.RefreshTokenRequest;
import com.voicepay.userservice.model.User;
import com.voicepay.userservice.repository.UserRepository;
import com.voicepay.userservice.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Operaciones de registro, inicio de sesión y renovación de tokens JWT")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión", description = "Autentica al usuario con email y contraseña, devolviendo un token de acceso JWT y un token de refresco.")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new RuntimeException("Credenciales inválidas");
            }

            String token = jwtUtil.generateToken(user.getEmail(), user.getRole());
            String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

            return ResponseEntity.ok(AuthResponse.builder()
                    .token(token)
                    .refreshToken(refreshToken)
                    .email(user.getEmail())
                    .role(user.getRole())
                    .build());
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new java.util.HashMap<String, String>() {{
                put("error", "Unauthorized");
                put("message", e.getMessage());
            }});
        }
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar token de acceso", description = "Permite obtener un nuevo token de acceso JWT utilizando un token de refresco válido.")
    public ResponseEntity<?> refresh(@RequestBody RefreshTokenRequest request) {
        try {
            String refreshToken = request.getRefreshToken();
            if (refreshToken != null && jwtUtil.isTokenValid(refreshToken)) {
                String email = jwtUtil.extractEmail(refreshToken);
                User user = userRepository.findByEmail(email)
                        .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
                
                String newToken = jwtUtil.generateToken(user.getEmail(), user.getRole());
                
                return ResponseEntity.ok(AuthResponse.builder()
                        .token(newToken)
                        .refreshToken(refreshToken)
                        .email(user.getEmail())
                        .role(user.getRole())
                        .build());
            } else {
                return ResponseEntity.status(401).body(new java.util.HashMap<String, String>() {{
                    put("error", "Unauthorized");
                    put("message", "Token de refresco inválido");
                }});
            }
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new java.util.HashMap<String, String>() {{
                put("error", "Unauthorized");
                put("message", e.getMessage());
            }});
        }
    }

    @PostMapping("/register")
    @Operation(summary = "Registrar nuevo usuario", description = "Registra un nuevo usuario en el sistema con contraseña encriptada y asignación de rol por defecto (ROLE_USER).")
    public ResponseEntity<?> register(@RequestBody User user) {
        try {
            if (user.getPassword() == null || user.getPassword().isEmpty()) {
                return ResponseEntity.badRequest().body("Password is required");
            }
            
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            
            // Manejo robusto del rol
            String role = user.getRole();
            if (role == null || role.isEmpty()) {
                role = "ROLE_USER";
            } else if (!role.toUpperCase().startsWith("ROLE_")) {
                role = "ROLE_" + role.toUpperCase();
            }
            user.setRole(role);
            
            return ResponseEntity.ok(userRepository.save(user));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error registering user: " + e.getMessage());
        }
    }
}
