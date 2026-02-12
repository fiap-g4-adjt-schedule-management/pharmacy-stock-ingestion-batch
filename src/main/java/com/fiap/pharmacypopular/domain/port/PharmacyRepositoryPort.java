package com.fiap.pharmacypopular.domain.port;

public interface PharmacyRepositoryPort {
    boolean existsByCnpj(String cnpj);
}
