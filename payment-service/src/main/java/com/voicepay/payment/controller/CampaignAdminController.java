package com.voicepay.payment.controller;

import com.voicepay.payment.client.UserServiceClient;
import com.voicepay.payment.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
@Tag(name = "Administración de Campañas", description = "Endpoints para la gestión, carga y monitoreo de campañas")
public class CampaignAdminController {

    private final UserServiceClient userServiceClient;
    private final JwtUtil jwtUtil;

    private HttpHeaders getHeadersWithJwt() {
        HttpHeaders headers = new HttpHeaders();
        String token = jwtUtil.generateToken("payment-service", "ROLE_ADMIN");
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Crear campaña con archivo de contactos", description = "Carga una lista de contactos (CSV o JSON) y crea la campaña.")
    public ResponseEntity<Object> createCampaign(
            @RequestParam("name") String name,
            @RequestParam(value = "maxRetries", defaultValue = "3") Integer maxRetries,
            @RequestParam(value = "commerceId", defaultValue = "1") Long commerceId,
            @RequestParam("file") MultipartFile file) {

        log.info("Request to create campaign '{}' with file: {}", name, file.getOriginalFilename());

        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El nombre de la campaña es obligatorio"));
        }

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El archivo de contactos es obligatorio y no puede estar vacío"));
        }

        List<Map<String, Object>> members = new ArrayList<>();
        String filename = file.getOriginalFilename();
        if (filename == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Nombre de archivo no válido"));
        }

        String lowerFilename = filename.toLowerCase();
        if (lowerFilename.endsWith(".csv") || "text/csv".equals(file.getContentType())) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                String headerLine = reader.readLine();
                if (headerLine == null) {
                    return ResponseEntity.badRequest().body(Map.of("error", "El archivo CSV está vacío"));
                }

                String[] headers = headerLine.split(",");
                int userIdIdx = -1;
                int userNameIdx = -1;
                int phoneIdx = -1;
                int debtIdx = -1;

                for (int i = 0; i < headers.length; i++) {
                    String h = headers[i].trim().toLowerCase().replace("_", "");
                    if (h.equals("userid") || h.equals("id")) {
                        userIdIdx = i;
                    } else if (h.equals("username") || h.equals("name")) {
                        userNameIdx = i;
                    } else if (h.equals("phonenumber") || h.equals("phone")) {
                        phoneIdx = i;
                    } else if (h.equals("associateddebt") || h.equals("debt") || h.equals("amount")) {
                        debtIdx = i;
                    }
                }

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    String[] parts = line.split(",");

                    Long userId = null;
                    String userName = null;
                    String phoneNumber = null;
                    Double associatedDebt = null;

                    if (userIdIdx != -1 && userIdIdx < parts.length) {
                        try { userId = Long.parseLong(parts[userIdIdx].trim()); } catch (Exception ignored) {}
                    }
                    if (userNameIdx != -1 && userNameIdx < parts.length) {
                        userName = parts[userNameIdx].trim();
                    }
                    if (phoneIdx != -1 && phoneIdx < parts.length) {
                        phoneNumber = parts[phoneIdx].trim();
                    }
                    if (debtIdx != -1 && debtIdx < parts.length) {
                        try { associatedDebt = Double.parseDouble(parts[debtIdx].trim()); } catch (Exception ignored) {}
                    }

                    if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                        continue;
                    }

                    Map<String, Object> member = new HashMap<>();
                    member.put("userId", userId);
                    member.put("userName", userName);
                    member.put("phoneNumber", phoneNumber.trim());
                    member.put("associatedDebt", associatedDebt != null ? associatedDebt : 0.0);
                    members.add(member);
                }
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Error al parsear el archivo CSV: " + e.getMessage()));
            }
        } else if (lowerFilename.endsWith(".json") || "application/json".equals(file.getContentType())) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                List<Map<String, Object>> parsed = mapper.readValue(file.getInputStream(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});

                for (Map<String, Object> item : parsed) {
                    Object phone = item.get("phoneNumber");
                    if (phone == null) phone = item.get("phone");
                    Object debt = item.get("associatedDebt");
                    if (debt == null) debt = item.get("debt");
                    if (debt == null) debt = item.get("amount");
                    Object uid = item.get("userId");
                    if (uid == null) uid = item.get("id");
                    Object nameVal = item.get("userName");
                    if (nameVal == null) nameVal = item.get("name");

                    if (phone == null || phone.toString().trim().isEmpty()) {
                        continue;
                    }

                    Map<String, Object> member = new HashMap<>();
                    if (uid != null) {
                        try { member.put("userId", Long.parseLong(uid.toString())); } catch (Exception ignored) {}
                    }
                    member.put("userName", nameVal != null ? nameVal.toString() : null);
                    member.put("phoneNumber", phone.toString().trim());
                    if (debt != null) {
                        try { member.put("associatedDebt", Double.parseDouble(debt.toString())); } catch (Exception ignored) {}
                    } else {
                        member.put("associatedDebt", 0.0);
                    }
                    members.add(member);
                }
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Error al parsear el archivo JSON: " + e.getMessage()));
            }
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Formato de archivo no soportado. Debe ser CSV o JSON."));
        }

        if (members.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No se encontraron contactos válidos en el archivo"));
        }

        Map<String, Object> campaignRequest = new HashMap<>();
        campaignRequest.put("name", name);
        campaignRequest.put("maxRetries", maxRetries);
        campaignRequest.put("commerceId", commerceId);
        campaignRequest.put("members", members);

        try {
            Object result = userServiceClient.createCampaign(campaignRequest, getHeadersWithJwt());
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (Exception e) {
            log.error("Failed to forward create campaign to user-service: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error de comunicación con el servicio de usuarios: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Actualizar estado de la campaña", description = "Modifica el estado del ciclo de vida (iniciar/pausar) de la campaña.")
    public ResponseEntity<Object> updateCampaignStatus(
            @PathVariable("id") Long id,
            @RequestParam("status") String status) {

        log.info("Request to update status of campaign {} to {}", id, status);

        if (status == null || status.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El parámetro status es obligatorio"));
        }

        String upperStatus = status.trim().toUpperCase();
        if (!upperStatus.equals("ACTIVE") && !upperStatus.equals("PAUSED") &&
            !upperStatus.equals("DRAFT") && !upperStatus.equals("COMPLETED")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Estado no válido. Debe ser uno de: ACTIVE, PAUSED, DRAFT, COMPLETED"));
        }

        try {
            Object result = userServiceClient.updateCampaignStatus(id, upperStatus, getHeadersWithJwt());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to update status for campaign {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al actualizar el estado de la campaña: " + e.getMessage()));
        }
    }

    @GetMapping("/reports")
    @Operation(summary = "Obtener reporte agregador de llamadas exitosas", description = "Devuelve estadísticas e informes de llamadas completadas exitosamente.")
    public ResponseEntity<Object> getCampaignsReport() {
        log.info("Request to fetch campaigns call report");
        try {
            Object report = userServiceClient.getCampaignsReport(getHeadersWithJwt());
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("Failed to retrieve campaigns report: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al recuperar el reporte de campañas: " + e.getMessage()));
        }
    }
}
