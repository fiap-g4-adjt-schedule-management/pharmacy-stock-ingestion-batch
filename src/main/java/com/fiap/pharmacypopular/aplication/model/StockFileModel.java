package com.fiap.pharmacypopular.aplication.model;

import java.time.LocalDate;

public record StockFileModel(
        String cnpj,
        String medicineName,
        int quantity,
        LocalDate referenceDate
) {}
