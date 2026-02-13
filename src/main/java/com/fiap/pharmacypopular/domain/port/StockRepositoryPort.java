package com.fiap.pharmacypopular.domain.port;

import com.fiap.pharmacypopular.domain.model.StockEntry;

import java.util.List;

public interface StockRepositoryPort {
    void upsertAll(List<StockEntry> rows);
}
