package com.fiap.pharmacypopular.aplication.service;

import com.fiap.pharmacypopular.aplication.model.StockModel;
import com.fiap.pharmacypopular.domain.model.StockStatus;
import com.fiap.pharmacypopular.domain.service.StockStatusCalculator;

import java.util.ArrayList;
import java.util.List;

public class StockProcessorStatusService {

    private final StockStatusCalculator calculator;

    public StockProcessorStatusService(StockStatusCalculator calculator) {
        this.calculator = calculator;
    }

    public List<StockModel> process(List<StockModel> rows) {
        List<StockModel> out = new ArrayList<>();
        for (StockModel r : rows) {
            StockStatus status = calculator.calculate(r.quantity());
            out.add(new StockModel(
                    r.cnpj(),
                    r.medicineName(),
                    r.medicineCode(),
                    r.quantity(),
                    r.referenceDate(),
                    status
            ));
        }
        return out;
    }
}
