package com.fiap.pharmacypopular.domain.port;

import com.fiap.pharmacypopular.domain.model.IngestionControlModel;

import java.time.LocalDate;
import java.util.Optional;

public interface IngestionControlRepositoryPort {

    Optional<IngestionControlModel> findByBlobPathAndEtag(String blobPath, String etag);

    Optional<Long> startProcessing(
            String blobPath,
            String etag,
            String fileName,
            String cnpj,
            LocalDate referenceDate
    );

    void markProcessed(long id);

    void markFailed(long id, String errorReason);
}
