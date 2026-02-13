package com.fiap.pharmacypopular.aplication.service;

import com.fiap.pharmacypopular.aplication.model.StockFileModel;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class StockFileParserService {

    private static final String EXPECTED_HEADER = "cnpj;medicine_name;quantity;reference_date";

    public List<StockFileModel> parse(byte[] bytes, String fileName, String expectedCnpj, LocalDate expectedReferenceDate) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Empty file: " + fileName);
        }

        String content = new String(bytes, StandardCharsets.UTF_8);
        String[] lines = content.split("\\r?\\n");

        if (lines.length < 2) {
            throw new IllegalArgumentException("CSV must contain header and at least one data line: " + fileName);
        }
        String header = lines[0].trim();
        if (!EXPECTED_HEADER.equalsIgnoreCase(header)) {
            throw new IllegalArgumentException("Invalid CSV header. Expected: " + EXPECTED_HEADER + " file=" + fileName);
        }

        List<StockFileModel> rows = new ArrayList<>();

        for (int i = 1; i < lines.length; i++) {
            String raw = lines[i].trim();
            if (raw.isEmpty()) continue;

            String[] parts = raw.split(";", -1);
            if (parts.length != 4) {
                throw new IllegalArgumentException("Invalid CSV line (expected 4 columns) at line " + (i + 1) + " file=" + fileName);
            }

            String cnpj = onlyDigits(parts[0].trim());
            String medicineName = parts[1].trim();
            String quantityStr = parts[2].trim();
            String dateStr = parts[3].trim();

            if (cnpj.length() != 14) {
                throw new IllegalArgumentException("Invalid CNPJ at line " + (i + 1) + ": " + parts[0].trim() + " file=" + fileName);
            }
            if (!cnpj.equals(expectedCnpj)) {
                throw new IllegalArgumentException("CNPJ mismatch at line " + (i + 1) + ": csv=" + cnpj + " expected=" + expectedCnpj + " file=" + fileName);
            }

            if (medicineName.isBlank()) {
                throw new IllegalArgumentException("medicine_name is required at line " + (i + 1) + " file=" + fileName);
            }

            int quantity;
            try {
                quantity = Integer.parseInt(quantityStr);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid quantity at line " + (i + 1) + ": " + quantityStr + " file=" + fileName);
            }

            if (quantity < 0) {
                throw new IllegalArgumentException("Quantity must be >= 0 at line " + (i + 1) + " file=" + fileName);
            }

            LocalDate referenceDate;
            try {
                referenceDate = LocalDate.parse(dateStr);
            } catch (Exception ex) {
                throw new IllegalArgumentException("Invalid reference_date at line " + (i + 1) + ": " + dateStr + " file=" + fileName);
            }
            if (!referenceDate.equals(expectedReferenceDate)) {
                throw new IllegalArgumentException("reference_date mismatch at line " + (i + 1) + ": csv=" + referenceDate
                        + " expected=" + expectedReferenceDate + " file=" + fileName);
            }

            rows.add(new StockFileModel(cnpj, medicineName, quantity, referenceDate));
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("CSV contains no data rows: " + fileName);
        }

        return rows;
    }

    private String onlyDigits(String s) {
        return s.replaceAll("\\D", "");
    }
}
