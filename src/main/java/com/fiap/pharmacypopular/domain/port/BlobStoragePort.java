package com.fiap.pharmacypopular.domain.port;

import java.time.OffsetDateTime;
import java.util.List;

public interface BlobStoragePort {

    record BlobRef(String name, String etag, OffsetDateTime lastModified) {}

    List<BlobRef> listInboxBlobs();

    byte[] download(String blobName);

    void moveToProcessed(String inboxBlobName);

    void moveToError(String inboxBlobName);
}
