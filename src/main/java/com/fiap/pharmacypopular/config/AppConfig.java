package com.fiap.pharmacypopular.config;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.fiap.pharmacypopular.adapter.blob.AzureBlobStorageAdapter;
import com.fiap.pharmacypopular.aplication.IngestStockFilesUseCase;
import com.fiap.pharmacypopular.aplication.service.FileStockValidator;
import com.fiap.pharmacypopular.domain.port.BlobStoragePort;

public class AppConfig {
    private AppConfig() {}

    public static IngestStockFilesUseCase useCase() {
        return Holder.USE_CASE;
    }

    private static final class Holder {
        private static final IngestStockFilesUseCase USE_CASE = buildUseCase();
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
        FileStockValidator validator = new FileStockValidator();

        return new IngestStockFilesUseCase(blobPort, minAgeMinutes, validator);
    }

    private static String env(String key) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Missing env var: " + key);
        }
        return v;
    }

    private static String envOr(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}
