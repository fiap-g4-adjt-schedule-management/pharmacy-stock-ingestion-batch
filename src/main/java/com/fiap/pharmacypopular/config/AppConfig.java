package com.fiap.pharmacypopular.config;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.fiap.pharmacypopular.adapter.blob.AzureBlobStorageAdapter;
import com.fiap.pharmacypopular.adapter.db.IngestionControlAdapter;
import com.fiap.pharmacypopular.adapter.db.MedicationRepositoryAdapter;
import com.fiap.pharmacypopular.adapter.db.PharmacyRepositoryAdapter;
import com.fiap.pharmacypopular.adapter.db.StockRepositoryAdapter;
import com.fiap.pharmacypopular.aplication.IngestStockFilesUseCase;
import com.fiap.pharmacypopular.aplication.service.FileStockValidatorService;
import com.fiap.pharmacypopular.aplication.service.StockFileParserService;
import com.fiap.pharmacypopular.aplication.service.StockMedicationCodeService;
import com.fiap.pharmacypopular.aplication.service.StockProcessorStatusService;
import com.fiap.pharmacypopular.domain.port.*;
import com.fiap.pharmacypopular.domain.service.StockStatusCalculator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

public class AppConfig {
    private AppConfig() {}

    public static IngestStockFilesUseCase useCase() {
        return Holder.USE_CASE;
    }

    private static final class Holder {
        private static final IngestStockFilesUseCase USE_CASE = buildUseCase();
    }

    private static DataSource buildDataSource() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(env("DB_URL"));
        cfg.setUsername(env("DB_USER"));
        cfg.setPassword(env("DB_PASSWORD"));
        cfg.setMaximumPoolSize(3);
        return new HikariDataSource(cfg);
    }

    private static IngestStockFilesUseCase buildUseCase() {
        String blobConn = env("BLOB_CONNECTION");
        String containerName = env("BLOB_CONTAINER");
        String inboxPrefix = env("INBOX_PREFIX");
        String processedPrefix = envOr("PROCESSED_PREFIX", "processed/");
        String errorPrefix = envOr("ERROR_PREFIX", "error/");
        int minAgeMinutes = Integer.parseInt(envOr("MIN_BLOB_AGE_MINUTES", "15"));

        BlobServiceClient serviceClient = new BlobServiceClientBuilder()
                .connectionString(blobConn)
                .buildClient();

        BlobContainerClient container = serviceClient.getBlobContainerClient(containerName);
        BlobStoragePort blobPort = new AzureBlobStorageAdapter(container, inboxPrefix, processedPrefix, errorPrefix);
        FileStockValidatorService validator = new FileStockValidatorService();
        DataSource ds = buildDataSource();
        PharmacyRepositoryPort pharmacyRepo = new PharmacyRepositoryAdapter(ds);
        IngestionControlRepositoryPort ingestionRepo = new IngestionControlAdapter(ds);
        StockFileParserService csvParser = new StockFileParserService();
        StockProcessorStatusService rowsProcessor = new StockProcessorStatusService(new StockStatusCalculator());
        MedicationRepositoryPort medicationRepo = new MedicationRepositoryAdapter(ds);
        StockMedicationCodeService rowsMedicationCodeResolver = new StockMedicationCodeService(medicationRepo);
        StockRepositoryPort stockRepo = new StockRepositoryAdapter(ds);

        return new IngestStockFilesUseCase(blobPort, minAgeMinutes, validator, pharmacyRepo, ingestionRepo,
                csvParser, rowsProcessor, rowsMedicationCodeResolver, stockRepo);
    }

    private static String env(String key) {
        String env = System.getenv(key);
        if (env == null || env.isBlank()) {
            throw new IllegalStateException("Missing env var: " + key);
        }
        return env;
    }

    private static String envOr(String key, String def) {
        String env = System.getenv(key);
        return (env == null || env.isBlank()) ? def : env;
    }
}
