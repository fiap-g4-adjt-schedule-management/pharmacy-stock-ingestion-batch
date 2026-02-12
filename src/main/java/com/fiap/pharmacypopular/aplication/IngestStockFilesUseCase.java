package com.fiap.pharmacypopular.aplication;

import com.fiap.pharmacypopular.adapter.exception.DestinationAlreadyExistsException;
import com.fiap.pharmacypopular.aplication.service.FileStockValidator;
import com.fiap.pharmacypopular.domain.port.BlobStoragePort;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

public class IngestStockFilesUseCase {

    private final BlobStoragePort blobPort;
    private final int minAgeMinutes;
    private final FileStockValidator validator;

    private static final Logger LOGGER = Logger.getLogger("IngestStockFilesUseCase");

    public IngestStockFilesUseCase(BlobStoragePort blobPort, int minAgeMinutes, FileStockValidator validator) {
        this.blobPort = blobPort;
        this.minAgeMinutes = minAgeMinutes;
        this.validator = validator;
    }

    public BatchRunResult execute() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(minAgeMinutes);

        List<BlobStoragePort.BlobRef> blobs = blobPort.listInboxBlobs().stream()
                .filter(b -> b.lastModified().isBefore(cutoff))
                .sorted(Comparator.comparing(BlobStoragePort.BlobRef::lastModified))
                .toList();

        int processed = 0;
        int failed = 0;
        int duplicates = 0;

        for (BlobStoragePort.BlobRef b : blobs) {
            try {
                byte[] bytes = blobPort.download(b.name());
                validator.validate(bytes, b.name());
                blobPort.moveToProcessed(b.name());
                processed++;
            } catch (DestinationAlreadyExistsException e) {
                LOGGER.warning("Duplicate target detected (not moving): blob=" + b.name() + " reason=" + e.getMessage());
                duplicates++;
            } catch (Exception e) {
                failed++;
                LOGGER.severe("Failed processing blob=" + b.name() + " error=" + e.getMessage());
                try {
                    blobPort.moveToError(b.name());
                } catch (Exception moveErr) {
                    LOGGER.severe("Failed moving blob to error/: blob=" + b.name() + " error=" + moveErr.getMessage());
                }
            }
        }
        return new BatchRunResult(blobs.size(), processed, failed, duplicates);
    }
}

