package com.fiap.pharmacypopular.aplication.model;

import com.fiap.pharmacypopular.domain.model.StockStatus;

import java.time.LocalDate;

public record StockModel(
        String cnpj,
        String medicineName,
        String medicineCode,
        int quantity,
        LocalDate referenceDate,
        StockStatus status
) {}