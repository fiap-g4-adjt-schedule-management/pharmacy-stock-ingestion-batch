package com.fiap.pharmacypopular.aplication.service;

import com.fiap.pharmacypopular.aplication.exception.FileValidationException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public class FileStockValidator {

    private static final List<String> EXPECTED_HEADER =
            List.of("cnpj", "medicine_name", "quantity", "reference_date");

    public void validate(byte[] bytes, String blobName) {
        char delimiter = ';';

        if (!blobName.toLowerCase().endsWith(".csv")) {
            throw new FileValidationException("Invalid file extension (expected .csv): " + blobName);
        }

        String content = new String(bytes, StandardCharsets.UTF_8);
        if (content.isBlank()) {
            throw new FileValidationException("File is empty: " + blobName);
        }

        String[] lines = content.split("\\R");
        String headerLine = lines[0].trim();
        if (headerLine.isBlank()) {
            throw new FileValidationException("Header is empty: " + blobName);
        }

        String[] headerCols = splitAndTrim(headerLine, delimiter);
        if (headerCols.length != EXPECTED_HEADER.size()) {
            throw new FileValidationException("Header with an invalid number of columns: " + blobName);
        }

        for (int i = 0; i < EXPECTED_HEADER.size(); i++) {
            if (!EXPECTED_HEADER.get(i).equalsIgnoreCase(headerCols[i])) {
                throw new FileValidationException("Invalid header in column " + (i + 1) + ": expected value = "
                        + EXPECTED_HEADER.get(i) + " received value = " + headerCols[i] + " (" + blobName + ")");
            }
        }

        for (int lineIndex = 1; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex].trim();
            if (line.isBlank()) continue;

            String[] parts = splitAndTrim(line, delimiter);
            if (parts.length != EXPECTED_HEADER.size()) {
                throw new FileValidationException("Line " + (lineIndex + 1) + " with invalid columns ("
                        + parts.length + "): " + blobName);
            }

            String cnpj = parts[0];
            String medicineName = parts[1];
            String quantity = parts[2];
            String referenceDate = parts[3];

            validateCnpj(cnpj, blobName, lineIndex);
            validateMedicineName(medicineName, blobName, lineIndex);
            validateQuantity(quantity, blobName, lineIndex);
            validateReferenceDate(referenceDate, blobName, lineIndex);
        }
    }

    private String[] splitAndTrim(String line, char delimiter) {
        return Arrays.stream(line.split(String.valueOf(delimiter), -1))
                .map(String::trim)
                .toArray(String[]::new);
    }

    private void validateCnpj(String cnpj, String blobName, int lineIndex) {
        if (cnpj.isBlank()) {
            throw new FileValidationException("Line " + (lineIndex + 1) + " CNPJ is empty: " + blobName);
        }
    }

    private void validateMedicineName(String medicineName, String blobName, int lineIndex) {
        if (medicineName.isBlank()) {
            throw new FileValidationException("Line " + (lineIndex + 1) + " medicine_name is empty: " + blobName);
        }
    }

    private void validateQuantity(String value, String blobName, int lineIndex) {
        try {
            int quantity = Integer.parseInt(value);
            if (quantity < 0) {
                throw new FileValidationException("Line " + (lineIndex + 1) + " quantity < 0: " + blobName);
            }
        } catch (Exception e) {
            throw new FileValidationException("Line " + (lineIndex + 1) + " " + "quantity" + " invalid: "
                    + value + " (" + blobName + ")");
        }
    }

    private void validateReferenceDate(String value, String blobName, int lineIndex) {
        try {
            LocalDate.parse(value);
        } catch (Exception e) {
            throw new FileValidationException("Line " + (lineIndex + 1) + " " + "reference_date"
                    + " invalid (yyyy-MM-dd): " + value + " (" + blobName + ")");
        }
    }
}
