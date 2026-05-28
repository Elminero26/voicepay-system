package com.voicepay.payment.service;

import com.voicepay.payment.model.Payment;
import com.voicepay.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
public class ReportServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Spy
    private SignatureService signatureService = new SignatureService();

    @InjectMocks
    private ReportService reportService;

    private List<Payment> mockPayments;

    @BeforeEach
    public void setUp() {
        // Inicializamos SignatureService
        signatureService.init();

        mockPayments = new ArrayList<>();
        mockPayments.add(Payment.builder()
                .id(101L)
                .userId(1L)
                .amount(new BigDecimal("150.00"))
                .currency("EUR")
                .exchangeRate(BigDecimal.ONE)
                .convertedAmount(new BigDecimal("150.00"))
                .transactionId("TX-1001")
                .description("Servicio Premium VoicePay")
                .status(Payment.PaymentStatus.COMPLETED)
                .createdAt(LocalDateTime.now().minusDays(1))
                .build());

        mockPayments.add(Payment.builder()
                .id(102L)
                .userId(2L)
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .exchangeRate(new BigDecimal("0.92"))
                .convertedAmount(new BigDecimal("91.99"))
                .transactionId("TX-1002")
                .description("Suscripción Mensual IVR")
                .status(Payment.PaymentStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .build());

        mockPayments.add(Payment.builder()
                .id(103L)
                .userId(1L)
                .amount(new BigDecimal("50.00"))
                .currency("EUR")
                .exchangeRate(BigDecimal.ONE)
                .convertedAmount(new BigDecimal("50.00"))
                .transactionId("TX-1003")
                .description("Recarga de Saldo Fallida")
                .status(Payment.PaymentStatus.FAILED)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Test
    public void testGeneratePdfReport() {
        Mockito.when(paymentRepository.findFilteredPayments(any(), any(), any(), any()))
                .thenReturn(mockPayments);

        byte[] pdfBytes = reportService.generatePdfReport(null, null, null, null);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
        
        // Un archivo PDF válido empieza con "%PDF-"
        String pdfHeader = new String(pdfBytes, 0, 5);
        assertEquals("%PDF-", pdfHeader);
    }

    @Test
    public void testGenerateExcelReport() {
        Mockito.when(paymentRepository.findFilteredPayments(any(), any(), any(), any()))
                .thenReturn(mockPayments);

        byte[] excelBytes = reportService.generateExcelReport(null, null, null, null);

        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0);
        
        // Las firmas de los archivos zip/xlsx suelen empezar con PK (50 4B)
        assertEquals(0x50, excelBytes[0]); // 'P'
        assertEquals(0x4B, excelBytes[1]); // 'K'
    }

    @Test
    public void testVerifyReportSignature_Valid() {
        Mockito.when(paymentRepository.findFilteredPayments(any(), any(), any(), any()))
                .thenReturn(mockPayments);

        // Generamos el reporte para registrar la firma
        byte[] pdfBytes = reportService.generatePdfReport(null, null, null, null);
        assertNotNull(pdfBytes);

        // Obtenemos la firma generada directamente para probar la verificación
        // Simulamos la verificación
        byte[] hashData = getReportHashData(mockPayments, null, null, null, null);
        String sha256 = calculateSha256(hashData);
        String sig = signatureService.sign(sha256.getBytes());

        boolean isValid = reportService.verifyReportSignature(null, null, null, null, sig);
        assertTrue(isValid);
    }

    @Test
    public void testVerifyReportSignature_Invalid() {
        Mockito.when(paymentRepository.findFilteredPayments(any(), any(), any(), any()))
                .thenReturn(mockPayments);

        // Una firma alterada/inválida debe retornar false
        boolean isValid = reportService.verifyReportSignature(null, null, null, null, "invalidSignatureBase64String");
        assertFalse(isValid);
    }

    // Métodos auxiliares para reproducir el cálculo en el test
    private byte[] getReportHashData(List<Payment> payments, Long userId, Payment.PaymentStatus status, LocalDateTime startDate, LocalDateTime endDate) {
        StringBuilder sb = new StringBuilder();
        sb.append("VoicePayFinancialReport;");
        sb.append("userId:").append(userId).append(";");
        sb.append("status:").append(status).append(";");
        sb.append("startDate:").append(startDate != null ? startDate.toString() : "null").append(";");
        sb.append("endDate:").append(endDate != null ? endDate.toString() : "null").append(";");
        for (Payment p : payments) {
            sb.append(p.getId()).append(",")
              .append(p.getUserId()).append(",")
              .append(p.getAmount().toPlainString()).append(",")
              .append(p.getCurrency()).append(",")
              .append(p.getStatus()).append(",")
              .append(p.getTransactionId() != null ? p.getTransactionId() : "null").append(",")
              .append(p.getCreatedAt() != null ? p.getCreatedAt().toString() : "null").append(";");
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String calculateSha256(byte[] data) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().toUpperCase();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
