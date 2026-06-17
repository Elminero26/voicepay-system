package com.voicepay.userservice.service;

import com.voicepay.userservice.dto.OtpGenerateResponse;
import com.voicepay.userservice.dto.OtpValidateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class OtpService {

    private final Map<String, OtpDetails> otpCache = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    public OtpGenerateResponse generateOtp(String identifier, int length, int ttlMinutes) {
        if (length != 4 && length != 6) {
            throw new IllegalArgumentException("La longitud del OTP debe ser 4 o 6 dígitos");
        }
        if (ttlMinutes <= 0) {
            throw new IllegalArgumentException("El TTL debe ser mayor a 0 minutos");
        }

        String code = generateNumericCode(length);
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(ttlMinutes);

        otpCache.put(identifier, new OtpDetails(code, expiryTime));
        log.info("Generado OTP para identificador '{}' con expiración {}", identifier, expiryTime);

        return OtpGenerateResponse.builder()
                .identifier(identifier)
                .code(code)
                .expiryTime(expiryTime)
                .build();
    }

    public OtpValidateResponse validateOtp(String identifier, String code) {
        OtpDetails details = otpCache.get(identifier);

        if (details == null) {
            log.warn("Intento de validación fallido: no existe OTP para el identificador '{}'", identifier);
            return OtpValidateResponse.builder()
                    .valid(false)
                    .message("No existe un código OTP activo para este identificador")
                    .build();
        }

        if (details.expiryTime().isBefore(LocalDateTime.now())) {
            otpCache.remove(identifier);
            log.warn("Intento de validación fallido: OTP expirado para el identificador '{}'", identifier);
            return OtpValidateResponse.builder()
                    .valid(false)
                    .message("El código OTP ha expirado")
                    .build();
        }

        if (!details.code().equals(code)) {
            log.warn("Intento de validación fallido: código incorrecto para el identificador '{}'", identifier);
            return OtpValidateResponse.builder()
                    .valid(false)
                    .message("Código OTP incorrecto")
                    .build();
        }

        // OTP válido: invalidar inmediatamente después de su primer uso exitoso
        otpCache.remove(identifier);
        log.info("OTP validado con éxito para el identificador '{}'", identifier);
        return OtpValidateResponse.builder()
                .valid(true)
                .message("OTP validado con éxito")
                .build();
    }

    @Scheduled(fixedRate = 60000) // Limpieza cada 1 minuto (60000 ms)
    public void cleanExpiredOtps() {
        LocalDateTime now = LocalDateTime.now();
        otpCache.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().expiryTime().isBefore(now);
            if (expired) {
                log.info("Limpieza automática: OTP expirado eliminado para '{}'", entry.getKey());
            }
            return expired;
        });
    }

    private String generateNumericCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(secureRandom.nextInt(10));
        }
        return sb.toString();
    }

    // Retornar mapa interno para pruebas unitarias
    protected Map<String, OtpDetails> getOtpCache() {
        return otpCache;
    }

    public static record OtpDetails(String code, LocalDateTime expiryTime) {}
}
