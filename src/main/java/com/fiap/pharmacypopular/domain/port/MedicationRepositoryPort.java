package com.fiap.pharmacypopular.domain.port;


import java.util.Optional;

public interface MedicationRepositoryPort {
    Optional<String> findCodeByName(String medicineName);
}
