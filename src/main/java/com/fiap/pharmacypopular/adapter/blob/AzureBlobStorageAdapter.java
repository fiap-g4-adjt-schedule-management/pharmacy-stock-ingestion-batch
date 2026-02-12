package com.fiap.pharmacypopular.adapter.blob;

import com.azure.core.util.polling.SyncPoller;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobCopyInfo;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.fiap.pharmacypopular.adapter.exception.DestinationAlreadyExistsException;
import com.fiap.pharmacypopular.domain.port.BlobStoragePort;
import com.fiap.pharmacypopular.adapter.exception.InfrastructureException;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class AzureBlobStorageAdapter implements BlobStoragePort {

    private final BlobContainerClient container;
    private final String inboxPrefix;
    private final String processedPrefix;
    private final String errorPrefix;

    public AzureBlobStorageAdapter(BlobContainerClient container, String inboxPrefix, String processedPrefix,
                                   String errorPrefix) {
        this.container = container;
        this.inboxPrefix = normalizePrefix(inboxPrefix);
        this.processedPrefix = normalizePrefix(processedPrefix);
        this.errorPrefix = normalizePrefix(errorPrefix);
    }

    @Override
    public List<BlobRef> listInboxBlobs() {
        var result = new ArrayList<BlobRef>();

        ListBlobsOptions options = new ListBlobsOptions()
                .setPrefix(inboxPrefix);

        for (BlobItem item : container.listBlobs(options, null)) {
            if (item.getProperties() == null) continue;

            String name = item.getName();
            String etag = item.getProperties().getETag();
            var lastModified = item.getProperties().getLastModified();

            if (lastModified != null) {
                result.add(new BlobRef(name, etag, lastModified));
            }
        }
        return result;
    }

    @Override
    public byte[] download(String blobName) {
        BlobClient blob = container.getBlobClient(blobName);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            blob.downloadStream(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new InfrastructureException("Failed to download blob: " + blobName, e);
        }
    }

    @Override
    public void moveToProcessed(String inboxBlobName) {
        moveReplacingPrefix(inboxBlobName, processedPrefix);
    }

    @Override
    public void moveToError(String inboxBlobName) {
        moveReplacingPrefix(inboxBlobName, errorPrefix);
    }

    private void moveReplacingPrefix(String inboxBlobName, String targetPrefix) {
        if (!inboxBlobName.startsWith(inboxPrefix)) {
            throw new InfrastructureException("Blob is not under inbox prefix: " + inboxBlobName);
        }

        String relative = inboxBlobName.substring(inboxPrefix.length());
        String targetBlobName = targetPrefix + relative;

        moveBlob(inboxBlobName, targetBlobName);
    }

    private void moveBlob(String sourceBlobName, String targetBlobName) {
        BlobClient source = container.getBlobClient(sourceBlobName);
        BlobClient target = container.getBlobClient(targetBlobName);

        try {
            if(Boolean.TRUE.equals(target.exists())){
                throw new DestinationAlreadyExistsException("Target blob already exists: " + targetBlobName);
            }
            SyncPoller<BlobCopyInfo, Void> poller =
                    target.beginCopy(source.getBlobUrl(), Duration.ofSeconds(1));

            poller.waitForCompletion();
            source.delete();
        } catch (DestinationAlreadyExistsException e) {
            throw e;
        } catch (Exception e) {
            throw new InfrastructureException("Failed to move blob from " + sourceBlobName +
                    " to " + targetBlobName, e
            );
        }
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) return "";
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }
}

