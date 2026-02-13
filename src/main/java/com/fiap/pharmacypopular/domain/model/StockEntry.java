package com.fiap.pharmacypopular.domain.model;


public record StockEntry(
        String cnpj,
        String medicineCode,
        int quantity,
        StockStatus status
) {}
