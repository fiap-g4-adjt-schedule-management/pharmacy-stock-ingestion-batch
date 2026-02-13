package com.fiap.pharmacypopular.aplication;

import com.fiap.pharmacypopular.adapter.exception.DestinationAlreadyExistsException;
import com.fiap.pharmacypopular.aplication.service.FileStockValidator;
import com.fiap.pharmacypopular.domain.port.BlobStoragePort;
import com.fiap.pharmacypopular.domain.port.IngestionControlRepositoryPort;
import com.fiap.pharmacypopular.domain.port.PharmacyRepositoryPort;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

import static com.fiap.pharmacypopular.domain.model.IngestStatus.FAILED;
import static com.fiap.pharmacypopular.domain.model.IngestStatus.PROCESSED;

public class IngestStockFilesUseCase {

    private static final Logger LOGGER = Logger.getLogger("IngestStockFilesUseCase");

    private final BlobStoragePort blobPort;
    private final int minAgeMinutes;
    private final FileStockValidator validator;
    private final PharmacyRepositoryPort pharmacyRepo;
    private final IngestionControlRepositoryPort ingestionRepo;

    public IngestStockFilesUseCase(BlobStoragePort blobPort, int minAgeMinutes, FileStockValidator validator,
                                   PharmacyRepositoryPort pharmacyRepo, IngestionControlRepositoryPort ingestionRepo) {
        this.blobPort = blobPort;
        this.minAgeMinutes = minAgeMinutes;
        this.validator = validator;
        this.pharmacyRepo = pharmacyRepo;
        this.ingestionRepo = ingestionRepo;
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
            BlobIngestionContext ctx = null;
            Long ingestionId = null;
            try{
                ctx = buildContext(b);

                if (handleIdempotencyAndReconcile(ctx)) {
                    duplicates++;
                    continue;
                }

                ingestionId = ingestionRepo.tryStartProcessing(
                        ctx.blobPath(), ctx.etag(), ctx.fileName(), ctx.cnpj(), ctx.referenceDate()
                ).orElse(null);

                if (ingestionId == null) {
                    duplicates++;
                    continue;
                }

                if (!pharmacyRepo.existsByCnpj(ctx.cnpj())) {
                    failed++;
                    fail(ingestionId, ctx.blobPath(), "Pharmacy CNPJ not found in database: " + ctx.cnpj());
                    continue;
                }
                byte[] bytes = blobPort.download(ctx.blobPath());
                validator.validate(bytes, ctx.fileName());

                succeed(ingestionId, ctx.blobPath());
                processed++;

            } catch (DestinationAlreadyExistsException e) {
                duplicates++;
                if (ingestionId != null) {
                    ingestionRepo.markProcessed(ingestionId);
                }
                String blobPath = safeBlobPath(ctx,b);
                moveToProcessedSafely(blobPath);
                LOGGER.warning("Duplicate target detected (not moving): blob=" + blobPath + " reason=" + e.getMessage());
            } catch (Exception e) {
                failed++;
                String blobPath = safeBlobPath(ctx,b);
                fail(ingestionId, blobPath, e.getMessage());
            }
        }
        return new BatchRunResult(blobs.size(), processed, failed, duplicates);
    }

    private BlobIngestionContext buildContext(BlobStoragePort.BlobRef b) {
        String blobPath = b.name();
        String etag = b.etag();
        String fileName = fileNameFromBlobPath(blobPath);
        String cnpj = extractCnpjFromBlobPath(blobPath);
        LocalDate referenceDate = extractReferenceDateFromFileName(fileName);
        return new BlobIngestionContext(blobPath, etag, fileName, cnpj, referenceDate);
    }

    private boolean handleIdempotencyAndReconcile(BlobIngestionContext ctx) {
        var existing = ingestionRepo.findByBlobPathAndEtag(ctx.blobPath(), ctx.etag());

        if (existing.isEmpty()){
            return false;
        }

        var status = existing.get().status();

        try {
            if (status == PROCESSED) {
                blobPort.moveToProcessed(ctx.blobPath());
            } else if (status == FAILED) {
                blobPort.moveToError(ctx.blobPath());
            }
        } catch (Exception ex) {
            LOGGER.warning("Failed to reconcile blob location: blob=" + ctx.blobPath()
                    + " status=" + status + " error= " + ex.getMessage());
        }
        return true;
    }

    private void moveToErrorSafely(String blobPath) {
        try {
            blobPort.moveToError(blobPath);
        } catch (Exception ex) {
            LOGGER.severe("Failed moving blob to error/: blob=" + blobPath + " error=" + ex.getMessage());
        }
    }

    private void moveToProcessedSafely(String blobPath) {
        try {
            blobPort.moveToProcessed(blobPath);
        } catch (Exception ex) {
            LOGGER.severe("Failed moving blob to processed/: blob=" + blobPath + " error=" + ex.getMessage());
        }
    }

    private void succeed(long ingestionId, String blobPath) {
        ingestionRepo.markProcessed(ingestionId);
        moveToProcessedSafely(blobPath);
    }

    private String extractCnpjFromBlobPath(String blobPath) {
        String[] parts = blobPath.split("/");
        if (parts.length < 3 || !"inbox".equals(parts[0])) {
            throw new IllegalArgumentException("Invalid blob path pattern: " + blobPath);
        }
        return parts[1];
    }

    private String fileNameFromBlobPath(String blobPath) {
        return blobPath.substring(blobPath.lastIndexOf('/') + 1);
    }

    private LocalDate extractReferenceDateFromFileName(String fileName) {
        String base = fileName.replace(".csv", "");
        String[] parts = base.split("_");
        if (parts.length < 4) {
            throw new IllegalArgumentException("Invalid filename pattern: " + fileName);
        }
        return LocalDate.parse(parts[2]);
    }

    private void fail(Long ingestionId, String blobPath, String reason) {
        LOGGER.severe("Failed processing blob=" + blobPath + " error=" + reason);

        if (ingestionId != null) {
            try {
                ingestionRepo.markFailed(ingestionId, reason);
            } catch (Exception ex) {
                LOGGER.severe("Failed marking ingestion as FAILED: id=" + ingestionId + " error=" + ex.getMessage());
            }
        }
        moveToErrorSafely(blobPath);
    }

    private String safeBlobPath(BlobIngestionContext ctx, BlobStoragePort.BlobRef b) {
        if (ctx != null) return ctx.blobPath();
        if (b != null) return b.name();
        return "unknown";
    }

}

