package com.fiap.pharmacypopular.aplication;

import java.time.LocalDate;

public record BlobIngestionContext(
        String blobPath,
        String etag,
        String fileName,
        String cnpj,
        LocalDate referenceDate
) {}
