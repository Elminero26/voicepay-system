package com.voicepay.payment.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.voicepay.payment.model.Payment;
import com.voicepay.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final PaymentRepository paymentRepository;
    private final SignatureService signatureService;

    // Colores corporativos de VoicePay (Aesthetics)
    private static final Color COLOR_PRIMARY = new Color(30, 41, 59);    // Slate 800 (Azul Grisáceo Oscuro)
    private static final Color COLOR_SECONDARY = new Color(59, 130, 246); // Blue 500 (Azul Brillante)
    private static final Color COLOR_SUCCESS = new Color(16, 185, 129);   // Emerald 500 (Verde)
    private static final Color COLOR_DANGER = new Color(239, 68, 68);     // Red 500 (Rojo)
    private static final Color COLOR_WARNING = new Color(245, 158, 11);   // Amber 500 (Naranja)
    private static final Color COLOR_BG_LIGHT = new Color(248, 250, 252); // Slate 50 (Gris Claro)
    private static final Color COLOR_BORDER = new Color(226, 232, 240);   // Slate 200 (Bordes)
    private static final Color COLOR_TEXT_MUTED = new Color(100, 116, 139);// Slate 500 (Texto Mutilado)

    /**
     * Obtiene los pagos filtrados del repositorio.
     */
    public List<Payment> getFilteredPayments(Long userId, Payment.PaymentStatus status, LocalDateTime startDate, LocalDateTime endDate) {
        return paymentRepository.findFilteredPayments(userId, status, startDate, endDate);
    }

    /**
     * Genera un informe PDF con una firma digital incrustada y diseño premium.
     */
    public byte[] generatePdfReport(Long userId, Payment.PaymentStatus status, LocalDateTime startDate, LocalDateTime endDate) {
        List<Payment> payments = getFilteredPayments(userId, status, startDate, endDate);
        
        // Generar hash y firma criptográfica
        byte[] hashData = calculateReportHashData(payments, userId, status, startDate, endDate);
        String sha256Hash = calculateSha256(hashData);
        String digitalSignature = signatureService.sign(sha256Hash.getBytes());
        String generationTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 40, 40);

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // --- 1. TÍTULO Y CABECERA ---
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, COLOR_PRIMARY);
            Paragraph title = new Paragraph("INFORME FINANCIERO DE TRANSACCIONES", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(5);
            document.add(title);

            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 10, COLOR_TEXT_MUTED);
            Paragraph subtitle = new Paragraph("VoicePay Secure Payment & IVR System • Reporte Oficial", subtitleFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(20);
            document.add(subtitle);

            // --- 2. METADATOS Y FILTROS (Tabla en paralelo) ---
            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setSpacingAfter(15);

            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, COLOR_PRIMARY);
            Font valFont = FontFactory.getFont(FontFactory.HELVETICA, 9, COLOR_PRIMARY);

            // Celda izquierda: Datos del Emisor
            PdfPCell leftCell = new PdfPCell();
            leftCell.setBorder(Rectangle.NO_BORDER);
            leftCell.addElement(new Paragraph("Detalles de Generación:", labelFont));
            leftCell.addElement(new Paragraph("• Emisor: VoicePay Financial Service", valFont));
            leftCell.addElement(new Paragraph("• Fecha: " + generationTime, valFont));
            leftCell.addElement(new Paragraph("• Estado del Canal: Firmado y Certificado", valFont));
            metaTable.addCell(leftCell);

            // Celda derecha: Filtros aplicados
            PdfPCell rightCell = new PdfPCell();
            rightCell.setBorder(Rectangle.NO_BORDER);
            rightCell.addElement(new Paragraph("Filtros Aplicados:", labelFont));
            rightCell.addElement(new Paragraph("• Usuario ID: " + (userId != null ? userId : "Todos"), valFont));
            rightCell.addElement(new Paragraph("• Estado de Pagos: " + (status != null ? status : "Todos"), valFont));
            String dateRange = "Sin restricción";
            if (startDate != null && endDate != null) {
                dateRange = startDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " al " + endDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } else if (startDate != null) {
                dateRange = "Desde " + startDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } else if (endDate != null) {
                dateRange = "Hasta " + endDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            }
            rightCell.addElement(new Paragraph("• Rango de Fechas: " + dateRange, valFont));
            metaTable.addCell(rightCell);
            document.add(metaTable);

            // --- 3. RESUMEN / ESTADÍSTICAS DEL INFORME (Tarjetas visuales) ---
            long completedCount = payments.stream().filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED).count();
            long failedCount = payments.stream().filter(p -> p.getStatus() == Payment.PaymentStatus.FAILED).count();
            BigDecimal totalEur = payments.stream()
                    .filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED)
                    .map(p -> p.getConvertedAmount() != null ? p.getConvertedAmount() : p.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            PdfPTable statsTable = new PdfPTable(3);
            statsTable.setWidthPercentage(100);
            statsTable.setSpacingAfter(20);

            // Estilo para tarjetas
            addStatCard(statsTable, "VOLUMEN PROCESADO", totalEur + " EUR", "Pagos completados convertidos a EUR");
            addStatCard(statsTable, "TRANSACCIONES COMPLETADAS", String.valueOf(completedCount), "Éxito en pasarela");
            addStatCard(statsTable, "TRANSACCIONES FALLIDAS", String.valueOf(failedCount), "Errores o rechazos");
            document.add(statsTable);

            // --- 4. TABLA DE TRANSACCIONES ---
            // Columnas: ID, Usuario, Fecha, Descripción, Estado, Importe Original, Importe EUR
            PdfPTable transTable = new PdfPTable(7);
            transTable.setWidthPercentage(100);
            transTable.setWidths(new float[]{1.0f, 1.2f, 2.0f, 2.8f, 1.5f, 2.0f, 2.0f});
            transTable.setSpacingAfter(30);

            // Encabezados
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
            String[] headers = {"ID", "Usuario", "Fecha / Hora", "Descripción", "Estado", "Original", "Monto Base"};
            for (String headerText : headers) {
                PdfPCell hCell = new PdfPCell(new Phrase(headerText, headerFont));
                hCell.setBackgroundColor(COLOR_PRIMARY);
                hCell.setPadding(6);
                hCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                hCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                hCell.setBorderColor(COLOR_BORDER);
                transTable.addCell(hCell);
            }

            // Filas
            Font rowFont = FontFactory.getFont(FontFactory.HELVETICA, 8, COLOR_PRIMARY);
            boolean isEven = false;
            for (Payment p : payments) {
                isEven = !isEven;
                Color rowBg = isEven ? COLOR_BG_LIGHT : Color.WHITE;

                // ID
                transTable.addCell(createTableCell(String.valueOf(p.getId()), rowFont, rowBg, Element.ALIGN_CENTER));
                
                // Usuario ID
                transTable.addCell(createTableCell("U-" + p.getUserId(), rowFont, rowBg, Element.ALIGN_CENTER));
                
                // Fecha
                String pDate = p.getCreatedAt() != null ? p.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "N/D";
                transTable.addCell(createTableCell(pDate, rowFont, rowBg, Element.ALIGN_CENTER));
                
                // Descripción
                String desc = p.getDescription() != null ? p.getDescription() : "Transacción VoicePay";
                transTable.addCell(createTableCell(desc, rowFont, rowBg, Element.ALIGN_LEFT));
                
                // Estado (Con Color Dinámico)
                PdfPCell statusCell = new PdfPCell();
                statusCell.setBackgroundColor(rowBg);
                statusCell.setPadding(5);
                statusCell.setBorderColor(COLOR_BORDER);
                statusCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                Color statusColor = COLOR_WARNING;
                if (p.getStatus() == Payment.PaymentStatus.COMPLETED) statusColor = COLOR_SUCCESS;
                else if (p.getStatus() == Payment.PaymentStatus.FAILED) statusColor = COLOR_DANGER;
                Font statusFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, statusColor);
                statusCell.addElement(new Paragraph(p.getStatus().toString(), statusFont));
                transTable.addCell(statusCell);
                
                // Importe Original
                String origAmount = p.getAmount() + " " + p.getCurrency();
                transTable.addCell(createTableCell(origAmount, rowFont, rowBg, Element.ALIGN_RIGHT));
                
                // Importe Base (EUR)
                BigDecimal eurAmount = p.getConvertedAmount() != null ? p.getConvertedAmount() : p.getAmount();
                transTable.addCell(createTableCell(eurAmount.setScale(2, RoundingMode.HALF_UP) + " EUR", rowFont, rowBg, Element.ALIGN_RIGHT));
            }
            
            if (payments.isEmpty()) {
                PdfPCell emptyCell = new PdfPCell(new Phrase("No hay transacciones registradas que coincidan con los filtros.", rowFont));
                emptyCell.setColspan(7);
                emptyCell.setPadding(10);
                emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                emptyCell.setBorderColor(COLOR_BORDER);
                transTable.addCell(emptyCell);
            }

            document.add(transTable);

            // --- 5. SECCIÓN DE FIRMA DIGITAL (Aesthetics & Security) ---
            PdfPTable sigBlock = new PdfPTable(1);
            sigBlock.setWidthPercentage(100);
            
            PdfPCell sigCell = new PdfPCell();
            sigCell.setBackgroundColor(COLOR_BG_LIGHT);
            sigCell.setBorderColor(COLOR_PRIMARY);
            sigCell.setBorderWidth(1.5f);
            sigCell.setPadding(12);

            Font sigTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, COLOR_PRIMARY);
            Font sigTextFont = FontFactory.getFont(FontFactory.HELVETICA, 7, COLOR_PRIMARY);
            Font sigMonospaceFont = FontFactory.getFont(FontFactory.COURIER, 6, COLOR_SECONDARY);

            sigCell.addElement(new Paragraph("CERTIFICACIÓN Y FIRMA DIGITAL CRIPTOGRÁFICA", sigTitleFont));
            Paragraph warningText = new Paragraph("Este documento está validado electrónicamente. La alteración de cualquier campo o transacción invalidará la firma digital.", sigTextFont);
            warningText.setSpacingAfter(8);
            sigCell.addElement(warningText);

            sigCell.addElement(new Paragraph("Autoridad de Firma: VOICEPAY SYSTEM AUTHORITY", sigTextFont));
            sigCell.addElement(new Paragraph("Algoritmo de Firma: SHA256withRSA (2048 bits)", sigTextFont));
            sigCell.addElement(new Paragraph("Huella del Reporte (SHA-256): " + sha256Hash, sigTextFont));
            
            Paragraph sigPara = new Paragraph("Sello de Firma RSA:\n" + wrapText(digitalSignature, 80), sigMonospaceFont);
            sigPara.setSpacingBefore(5);
            sigCell.addElement(sigPara);

            sigBlock.addCell(sigCell);
            document.add(sigBlock);

        } catch (Exception e) {
            log.error("Error al construir el PDF del informe", e);
        } finally {
            document.close();
        }

        return out.toByteArray();
    }

    /**
     * Genera un informe Excel con una firma digital y diseño premium.
     */
    public byte[] generateExcelReport(Long userId, Payment.PaymentStatus status, LocalDateTime startDate, LocalDateTime endDate) {
        List<Payment> payments = getFilteredPayments(userId, status, startDate, endDate);
        
        // Generar hash y firma criptográfica
        byte[] hashData = calculateReportHashData(payments, userId, status, startDate, endDate);
        String sha256Hash = calculateSha256(hashData);
        String digitalSignature = signatureService.sign(sha256Hash.getBytes());
        String generationTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Historial de Pagos");
            sheet.setDisplayGridlines(true);
            sheet.setPrintGridlines(true);

            // --- PALETA DE COLORES Y ESTILOS ---
            // Paleta de colores para POI
            byte[] colorPrimaryBytes = {(byte) 30, (byte) 41, (byte) 59};
            byte[] colorSecondaryBytes = {(byte) 59, (byte) 130, (byte) 246};
            byte[] colorBgLightBytes = {(byte) 248, (byte) 250, (byte) 252};
            
            XSSFColor colorPrimary = new XSSFColor(colorPrimaryBytes, null);
            XSSFColor colorSecondary = new XSSFColor(colorSecondaryBytes, null);
            XSSFColor colorBgLight = new XSSFColor(colorBgLightBytes, null);

            // Fuentes
            XSSFFont titleFont = workbook.createFont();
            titleFont.setFontName("Segoe UI");
            titleFont.setFontHeightInPoints((short) 16);
            titleFont.setBold(true);
            titleFont.setColor(colorPrimary);

            XSSFFont headerFont = workbook.createFont();
            headerFont.setFontName("Segoe UI");
            headerFont.setFontHeightInPoints((short) 10);
            headerFont.setBold(true);
            headerFont.setColor(new XSSFColor(Color.WHITE, null));

            XSSFFont boldFont = workbook.createFont();
            boldFont.setFontName("Segoe UI");
            boldFont.setFontHeightInPoints((short) 9);
            boldFont.setBold(true);
            boldFont.setColor(colorPrimary);

            XSSFFont regularFont = workbook.createFont();
            regularFont.setFontName("Segoe UI");
            regularFont.setFontHeightInPoints((short) 9);

            XSSFFont monospaceFont = workbook.createFont();
            monospaceFont.setFontName("Consolas");
            monospaceFont.setFontHeightInPoints((short) 8);
            monospaceFont.setColor(colorSecondary);

            // Estilos
            CellStyle styleTitle = workbook.createCellStyle();
            styleTitle.setFont(titleFont);
            styleTitle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle styleHeader = workbook.createCellStyle();
            styleHeader.setFont(headerFont);
            styleHeader.setFillForegroundColor(colorPrimary);
            styleHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            styleHeader.setAlignment(HorizontalAlignment.CENTER);
            styleHeader.setVerticalAlignment(VerticalAlignment.CENTER);
            styleHeader.setBorderBottom(BorderStyle.MEDIUM);
            styleHeader.setBorderLeft(BorderStyle.THIN);
            styleHeader.setBorderRight(BorderStyle.THIN);
            styleHeader.setBorderTop(BorderStyle.THIN);

            CellStyle styleEven = workbook.createCellStyle();
            styleEven.setFont(regularFont);
            styleEven.setBorderBottom(BorderStyle.THIN);
            styleEven.setBorderLeft(BorderStyle.THIN);
            styleEven.setBorderRight(BorderStyle.THIN);
            styleEven.setBorderTop(BorderStyle.THIN);

            CellStyle styleOdd = workbook.createCellStyle();
            styleOdd.setFont(regularFont);
            styleOdd.setFillForegroundColor(colorBgLight);
            styleOdd.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            styleOdd.setBorderBottom(BorderStyle.THIN);
            styleOdd.setBorderLeft(BorderStyle.THIN);
            styleOdd.setBorderRight(BorderStyle.THIN);
            styleOdd.setBorderTop(BorderStyle.THIN);

            // Estilos numéricos
            CellStyle styleEvenNum = workbook.createCellStyle();
            styleEvenNum.cloneStyleFrom(styleEven);
            styleEvenNum.setAlignment(HorizontalAlignment.RIGHT);
            styleEvenNum.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            CellStyle styleOddNum = workbook.createCellStyle();
            styleOddNum.cloneStyleFrom(styleOdd);
            styleOddNum.setAlignment(HorizontalAlignment.RIGHT);
            styleOddNum.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            // Estilo para estados
            CellStyle styleSuccess = workbook.createCellStyle();
            styleSuccess.cloneStyleFrom(styleEven);
            styleSuccess.setAlignment(HorizontalAlignment.CENTER);
            XSSFFont fontGreen = workbook.createFont();
            fontGreen.setFontName("Segoe UI");
            fontGreen.setFontHeightInPoints((short) 9);
            fontGreen.setBold(true);
            fontGreen.setColor(new XSSFColor(COLOR_SUCCESS, null));
            styleSuccess.setFont(fontGreen);

            CellStyle styleDanger = workbook.createCellStyle();
            styleDanger.cloneStyleFrom(styleEven);
            styleDanger.setAlignment(HorizontalAlignment.CENTER);
            XSSFFont fontRed = workbook.createFont();
            fontRed.setFontName("Segoe UI");
            fontRed.setFontHeightInPoints((short) 9);
            fontRed.setBold(true);
            fontRed.setColor(new XSSFColor(COLOR_DANGER, null));
            styleDanger.setFont(fontRed);

            // --- 1. CABECERA Y METADATOS ---
            Row rowTitle = sheet.createRow(1);
            rowTitle.setHeightInPoints(30);
            Cell cellTitle = rowTitle.createCell(1);
            cellTitle.setCellValue("INFORME FINANCIERO - VOICEPAY SYSTEM");
            cellTitle.setCellStyle(styleTitle);

            // Filtros y emisor
            Row rowMeta1 = sheet.createRow(3);
            rowMeta1.createCell(1).setCellValue("Detalles del Emisor:");
            rowMeta1.getCell(1).setCellStyle(createBoldStyle(workbook, boldFont));
            rowMeta1.createCell(2).setCellValue("VoicePay Financial Service");
            rowMeta1.getCell(2).setCellStyle(createRegularStyle(workbook, regularFont));
            rowMeta1.createCell(4).setCellValue("Filtro Usuario:");
            rowMeta1.getCell(4).setCellStyle(createBoldStyle(workbook, boldFont));
            rowMeta1.createCell(5).setCellValue(userId != null ? "U-" + userId : "Todos");
            rowMeta1.getCell(5).setCellStyle(createRegularStyle(workbook, regularFont));

            Row rowMeta2 = sheet.createRow(4);
            rowMeta2.createCell(1).setCellValue("Fecha Generación:");
            rowMeta2.getCell(1).setCellStyle(createBoldStyle(workbook, boldFont));
            rowMeta2.createCell(2).setCellValue(generationTime);
            rowMeta2.getCell(2).setCellStyle(createRegularStyle(workbook, regularFont));
            rowMeta2.createCell(4).setCellValue("Filtro Estado:");
            rowMeta2.getCell(4).setCellStyle(createBoldStyle(workbook, boldFont));
            rowMeta2.createCell(5).setCellValue(status != null ? status.toString() : "Todos");
            rowMeta2.getCell(5).setCellStyle(createRegularStyle(workbook, regularFont));

            // --- 2. SUMARIZACIÓN RÁPIDA ---
            long completedCount = payments.stream().filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED).count();
            BigDecimal totalEur = payments.stream()
                    .filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED)
                    .map(p -> p.getConvertedAmount() != null ? p.getConvertedAmount() : p.getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Row rowSummary = sheet.createRow(6);
            rowSummary.createCell(1).setCellValue("Total Completados:");
            rowSummary.getCell(1).setCellStyle(createBoldStyle(workbook, boldFont));
            rowSummary.createCell(2).setCellValue(completedCount);
            rowSummary.getCell(2).setCellStyle(createRegularStyle(workbook, regularFont));

            rowSummary.createCell(4).setCellValue("Monto Total Base (EUR):");
            rowSummary.getCell(4).setCellStyle(createBoldStyle(workbook, boldFont));
            Cell cellTotalVal = rowSummary.createCell(5);
            cellTotalVal.setCellValue(totalEur.doubleValue());
            CellStyle totalValStyle = workbook.createCellStyle();
            totalValStyle.setFont(boldFont);
            totalValStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00 \"EUR\""));
            cellTotalVal.setCellStyle(totalValStyle);

            // --- 3. TABLA DE TRANSACCIONES ---
            String[] headers = {"ID Pago", "Usuario ID", "Fecha", "Descripción", "Estado", "Divisa", "Monto Original", "Monto Base (EUR)"};
            Row rowHeaders = sheet.createRow(8);
            rowHeaders.setHeightInPoints(24);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = rowHeaders.createCell(i + 1);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(styleHeader);
            }

            int rowIdx = 9;
            for (Payment p : payments) {
                Row row = sheet.createRow(rowIdx);
                row.setHeightInPoints(18);
                boolean isEven = (rowIdx % 2 == 0);
                CellStyle currentStyle = isEven ? styleEven : styleOdd;
                CellStyle currentNumStyle = isEven ? styleEvenNum : styleOddNum;

                // ID Pago
                Cell c1 = row.createCell(1);
                c1.setCellValue(p.getId());
                c1.setCellStyle(currentStyle);

                // Usuario ID
                Cell c2 = row.createCell(2);
                c2.setCellValue("U-" + p.getUserId());
                c2.setCellStyle(currentStyle);

                // Fecha
                Cell c3 = row.createCell(3);
                String pDate = p.getCreatedAt() != null ? p.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "N/D";
                c3.setCellValue(pDate);
                c3.setCellStyle(currentStyle);

                // Descripción
                Cell c4 = row.createCell(4);
                c4.setCellValue(p.getDescription() != null ? p.getDescription() : "Transacción VoicePay");
                c4.setCellStyle(currentStyle);

                // Estado
                Cell c5 = row.createCell(5);
                c5.setCellValue(p.getStatus().toString());
                if (p.getStatus() == Payment.PaymentStatus.COMPLETED) {
                    c5.setCellStyle(styleSuccess);
                } else if (p.getStatus() == Payment.PaymentStatus.FAILED) {
                    c5.setCellStyle(styleDanger);
                } else {
                    c5.setCellStyle(currentStyle);
                }

                // Divisa
                Cell c6 = row.createCell(6);
                c6.setCellValue(p.getCurrency());
                c6.setCellStyle(currentStyle);

                // Monto Original
                Cell c7 = row.createCell(7);
                c7.setCellValue(p.getAmount().doubleValue());
                c7.setCellStyle(currentNumStyle);

                // Monto Base (EUR)
                Cell c8 = row.createCell(8);
                BigDecimal eurVal = p.getConvertedAmount() != null ? p.getConvertedAmount() : p.getAmount();
                c8.setCellValue(eurVal.doubleValue());
                c8.setCellStyle(currentNumStyle);

                rowIdx++;
            }

            if (payments.isEmpty()) {
                Row emptyRow = sheet.createRow(rowIdx);
                Cell cell = emptyRow.createCell(1);
                cell.setCellValue("No hay transacciones registradas para estos filtros.");
                cell.setCellStyle(styleEven);
                sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowIdx, rowIdx, 1, 8));
                rowIdx++;
            }

            // --- 4. BLOQUE DE FIRMA DIGITAL CRIPTOGRÁFICA ---
            rowIdx += 2;
            Row rowSigTitle = sheet.createRow(rowIdx);
            rowSigTitle.setHeightInPoints(20);
            Cell sigHeaderCell = rowSigTitle.createCell(1);
            sigHeaderCell.setCellValue("CERTIFICADO DE FIRMA DIGITAL Y AUTENTICIDAD");
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowIdx, rowIdx, 1, 8));
            
            CellStyle sigTitleStyle = workbook.createCellStyle();
            sigTitleStyle.setFont(boldFont);
            sigTitleStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)226, (byte)232, (byte)240}, null));
            sigTitleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            sigTitleStyle.setAlignment(HorizontalAlignment.LEFT);
            sigTitleStyle.setBorderTop(BorderStyle.THIN);
            sigTitleStyle.setBorderBottom(BorderStyle.HAIR);
            sigTitleStyle.setBorderLeft(BorderStyle.THIN);
            sigTitleStyle.setBorderRight(BorderStyle.THIN);
            sigHeaderCell.setCellStyle(sigTitleStyle);

            rowIdx++;
            Row rowSigInfo = sheet.createRow(rowIdx);
            Cell sigInfoCell = rowSigInfo.createCell(1);
            sigInfoCell.setCellValue("Este libro de Excel ha sido verificado criptográficamente por la autoridad central de VoicePay.");
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowIdx, rowIdx, 1, 8));
            CellStyle sigTextStyle = workbook.createCellStyle();
            sigTextStyle.setFont(regularFont);
            sigTextStyle.setFillForegroundColor(colorBgLight);
            sigTextStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            sigTextStyle.setBorderLeft(BorderStyle.THIN);
            sigTextStyle.setBorderRight(BorderStyle.THIN);
            sigInfoCell.setCellStyle(sigTextStyle);

            rowIdx++;
            Row rowHash = sheet.createRow(rowIdx);
            Cell hashCell = rowHash.createCell(1);
            hashCell.setCellValue("Huella SHA-256 del Reporte: " + sha256Hash);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowIdx, rowIdx, 1, 8));
            hashCell.setCellStyle(sigTextStyle);

            rowIdx++;
            Row rowSigBytes = sheet.createRow(rowIdx);
            rowSigBytes.setHeightInPoints(35);
            Cell sigBytesCell = rowSigBytes.createCell(1);
            sigBytesCell.setCellValue("Firma Criptográfica RSA:\n" + wrapText(digitalSignature, 90));
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowIdx, rowIdx, 1, 8));
            
            CellStyle sigValStyle = workbook.createCellStyle();
            sigValStyle.setFont(monospaceFont);
            sigValStyle.setFillForegroundColor(colorBgLight);
            sigValStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            sigValStyle.setWrapText(true);
            sigValStyle.setBorderLeft(BorderStyle.THIN);
            sigValStyle.setBorderRight(BorderStyle.THIN);
            sigValStyle.setBorderBottom(BorderStyle.THIN);
            sigBytesCell.setCellStyle(sigValStyle);

            // Ajustar columnas
            for (int i = 1; i <= 8; i++) {
                sheet.autoSizeColumn(i);
                // Si la columna es la descripción, darle un ancho generoso
                if (i == 4) {
                    sheet.setColumnWidth(i, 8000);
                }
            }

            workbook.write(out);
        } catch (Exception e) {
            log.error("Error al generar el informe de Excel", e);
        }

        return out.toByteArray();
    }

    /**
     * Valida una firma digital enviada en base a los parámetros y el conjunto de pagos actual.
     */
    public boolean verifyReportSignature(Long userId, Payment.PaymentStatus status, LocalDateTime startDate, LocalDateTime endDate, String signatureBase64) {
        List<Payment> payments = getFilteredPayments(userId, status, startDate, endDate);
        byte[] hashData = calculateReportHashData(payments, userId, status, startDate, endDate);
        String sha256Hash = calculateSha256(hashData);
        return signatureService.verify(sha256Hash.getBytes(), signatureBase64);
    }

    // --- MÉTODOS AUXILIARES ---

    private byte[] calculateReportHashData(List<Payment> payments, Long userId, Payment.PaymentStatus status, LocalDateTime startDate, LocalDateTime endDate) {
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
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().toUpperCase();
        } catch (Exception e) {
            throw new RuntimeException("Error al calcular SHA-256 del reporte", e);
        }
    }

    private void addStatCard(PdfPTable table, String title, String value, String desc) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(COLOR_BG_LIGHT);
        cell.setBorderColor(COLOR_BORDER);
        cell.setPadding(8);
        cell.setPaddingBottom(12);

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, COLOR_TEXT_MUTED);
        Font valFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, COLOR_PRIMARY);
        Font descFont = FontFactory.getFont(FontFactory.HELVETICA, 6, COLOR_TEXT_MUTED);

        cell.addElement(new Paragraph(title, titleFont));
        cell.addElement(new Paragraph(value, valFont));
        
        Paragraph pDesc = new Paragraph(desc, descFont);
        pDesc.setSpacingBefore(3);
        cell.addElement(pDesc);

        table.addCell(cell);
    }

    private PdfPCell createTableCell(String text, Font font, Color bgColor, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bgColor);
        cell.setPadding(6);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBorderColor(COLOR_BORDER);
        return cell;
    }

    private CellStyle createBoldStyle(Workbook wb, XSSFFont font) {
        CellStyle style = wb.createCellStyle();
        style.setFont(font);
        return style;
    }

    private CellStyle createRegularStyle(Workbook wb, XSSFFont font) {
        CellStyle style = wb.createCellStyle();
        style.setFont(font);
        return style;
    }

    private String wrapText(String text, int lineSize) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i += lineSize) {
            int end = Math.min(i + lineSize, text.length());
            sb.append(text, i, end).append("\n");
        }
        return sb.toString().trim();
    }
}
