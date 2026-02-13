package com.fiap.pharmacypopular.aplication.service;

import com.fiap.pharmacypopular.aplication.model.StockFileModel;
import com.fiap.pharmacypopular.aplication.model.StockModel;
import com.fiap.pharmacypopular.domain.port.MedicationRepositoryPort;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StockMedicationCodeService {

    private final MedicationRepositoryPort medicationRepo;

    public StockMedicationCodeService(MedicationRepositoryPort medicationRepo) {
        this.medicationRepo = medicationRepo;
    }

    public List<StockModel> process(List<StockFileModel> rows) {
        List<String> uniqueNames = rows.stream()
                .map(r -> r.medicineName().trim())
                .distinct()
                .toList();

        Map<String, String> codeByName = new HashMap<>();
        List<String> missing = new ArrayList<>();

        for (String name : uniqueNames) {
            medicationRepo.findCodeByName(name)
                    .ifPresentOrElse(
                            code -> codeByName.put(name, code),
                            () -> missing.add(name)
                    );
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Medication(s) not found in reference table: " + String.join(", ", missing));
        }

        List<StockModel> out = new ArrayList<>(rows.size());
        for (StockFileModel r : rows) {
            String name = r.medicineName().trim();
            out.add(new StockModel(
                    r.cnpj(),
                    r.medicineName(),
                    codeByName.get(name),
                    r.quantity(),
                    r.referenceDate(),
                    null
            ));
        }
        return out;
    }
}
