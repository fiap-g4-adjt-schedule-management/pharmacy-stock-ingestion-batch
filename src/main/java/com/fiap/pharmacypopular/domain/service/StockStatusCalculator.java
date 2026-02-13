package com.fiap.pharmacypopular.domain.service;

import com.fiap.pharmacypopular.domain.model.StockStatus;

public class StockStatusCalculator {

    public StockStatus calculate(int quantity) {
        if (quantity < 10) return StockStatus.CRITICAL;
        if (quantity <= 30) return StockStatus.NORMAL;
        return StockStatus.HIGH;
    }
}
