package com.voicepay.payment.controller;

import com.voicepay.payment.model.Payment;
import com.voicepay.payment.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/payments/reports")
@RequiredArgsConstructor
@Tag(name = "Reportes y Exportaciones", description = "Endpoints para la exportación de informes financieros y validación de firmas digitales")
@Slf4j
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/export/pdf")
    @Operation(summary = "Exportar informe en PDF", description = "Genera un informe financiero en PDF firmado digitalmente con filtros opcionales.")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Payment.PaymentStatus status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        
        log.info("Solicitud para exportar PDF. Filtros - Usuario: {}, Estado: {}, Desde: {}, Hasta: {}", userId, status, startDate, endDate);
        
        LocalDateTime start = parseDateTime(startDate, false);
        LocalDateTime end = parseDateTime(endDate, true);

        byte[] pdfBytes = reportService.generatePdfReport(userId, status, start, end);
        String filename = "informe-financiero-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".pdf";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    @GetMapping("/export/excel")
    @Operation(summary = "Exportar informe en Excel", description = "Genera una hoja de cálculo Excel firmada digitalmente con estilos corporativos y filtros opcionales.")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Payment.PaymentStatus status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        log.info("Solicitud para exportar Excel. Filtros - Usuario: {}, Estado: {}, Desde: {}, Hasta: {}", userId, status, startDate, endDate);

        LocalDateTime start = parseDateTime(startDate, false);
        LocalDateTime end = parseDateTime(endDate, true);

        byte[] excelBytes = reportService.generateExcelReport(userId, status, start, end);
        String filename = "informe-financiero-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".xlsx";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

        return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
    }

    @PostMapping("/verify")
    @Operation(summary = "Verificar firma digital del informe", description = "Comprueba si una firma digital es auténtica y si el contenido del informe no ha sido alterado.")
    public ResponseEntity<Map<String, Object>> verifySignature(@RequestBody Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            Long userId = payload.get("userId") != null ? Long.valueOf(payload.get("userId").toString()) : null;
            Payment.PaymentStatus status = payload.get("status") != null ? Payment.PaymentStatus.valueOf(payload.get("status").toString()) : null;
            String startDateStr = payload.get("startDate") != null ? payload.get("startDate").toString() : null;
            String endDateStr = payload.get("endDate") != null ? payload.get("endDate").toString() : null;
            String signature = payload.get("signature") != null ? payload.get("signature").toString() : null;

            if (signature == null || signature.trim().isEmpty()) {
                response.put("valid", false);
                response.put("message", "La firma digital criptográfica es obligatoria para la verificación.");
                return ResponseEntity.badRequest().body(response);
            }

            LocalDateTime start = parseDateTime(startDateStr, false);
            LocalDateTime end = parseDateTime(endDateStr, true);

            boolean isValid = reportService.verifyReportSignature(userId, status, start, end, signature);

            response.put("valid", isValid);
            if (isValid) {
                response.put("message", "¡Firma digital VERIFICADA con éxito! El documento es legítimo de VoicePay y no ha sido alterado.");
                response.put("verifiedAt", LocalDateTime.now().toString());
                response.put("authority", "VoicePay System Cryptographic Authority");
            } else {
                response.put("message", "Firma digital INVÁLIDA. El contenido del informe ha sido alterado o la firma no corresponde al emisor autorizado.");
            }
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error al verificar la firma digital", e);
            response.put("valid", false);
            response.put("message", "Error al procesar la verificación: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private LocalDateTime parseDateTime(String dateStr, boolean isEnd) {
        if (dateStr == null || dateStr.trim().isEmpty() || "null".equalsIgnoreCase(dateStr)) {
            return null;
        }
        try {
            if (dateStr.length() == 10) { // yyyy-MM-dd
                return isEnd 
                    ? LocalDate.parse(dateStr).atTime(23, 59, 59)
                    : LocalDate.parse(dateStr).atStartOfDay();
            }
            // Soporte para formato con espacio o T
            String formattedDate = dateStr.replace(" ", "T");
            if (formattedDate.contains("T")) {
                return LocalDateTime.parse(formattedDate);
            }
            return LocalDate.parse(dateStr).atStartOfDay();
        } catch (Exception e) {
            log.warn("Formato de fecha inválido o no reconocido: {}. Tratando como null. Detalle: {}", dateStr, e.getMessage());
            return null;
        }
    }
}
