package com.foundflow.photo.storage;

import com.azure.core.exception.AzureException;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.ParallelTransferOptions;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

public class AzurePhotoStorage implements PhotoStorage {

    private final BlobContainerClient containerClient;
    private final PhotoKeyFactory keyFactory;

    public static AzurePhotoStorage create(String connectionString, String containerName, String domain) {
        BlobContainerClient client = new BlobContainerClientBuilder()
                .connectionString(connectionString)
                .containerName(containerName)
                .buildClient();
        return new AzurePhotoStorage(client, domain);
    }

    AzurePhotoStorage(BlobContainerClient containerClient, String domain) {
        this.containerClient = containerClient;
        this.keyFactory = new PhotoKeyFactory(domain, Clock.systemUTC());
        ensureContainerExists();
    }

    @Override
    public String store(PhotoData photo) {
        String photoKey = keyFactory.createKey(photo.contentType());
        try (InputStream content = photo.content()) {
            BlobClient blob = containerClient.getBlobClient(photoKey);
            blob.uploadWithResponse(
                    new BlobParallelUploadOptions(content)
                            .setParallelTransferOptions(new ParallelTransferOptions())
                            .setHeaders(new BlobHttpHeaders().setContentType(photo.contentType())),
                    null,
                    null
            );
            return photoKey;
        } catch (IOException | AzureException exception) {
            throw new PhotoStorageException("Could not store photo.", exception);
        }
    }

    @Override
    public PhotoData retrieve(String photoKey) {
        try {
            BlobClient blob = containerClient.getBlobClient(photoKey);
            BlobProperties properties = blob.getProperties();
            byte[] payload = blob.downloadContent().toBytes();
            String contentType = properties.getContentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = keyFactory.contentTypeFor(photoKey);
            }
            return new PhotoData(new ByteArrayInputStream(payload), contentType, properties.getBlobSize());
        } catch (BlobStorageException exception) {
            if (BlobErrorCode.BLOB_NOT_FOUND.equals(exception.getErrorCode())) {
                throw new PhotoNotFoundException(photoKey, exception);
            }
            throw new PhotoStorageException("Could not retrieve photo.", exception);
        } catch (AzureException exception) {
            throw new PhotoStorageException("Could not retrieve photo.", exception);
        }
    }

    @Override
    public URI signedUrl(String photoKey, Duration ttl) {
        long ttlSeconds = ttl.getSeconds();
        if (ttlSeconds <= 0L || ttlSeconds > TimeUnit.DAYS.toSeconds(7L)) {
            throw new PhotoStorageException("Signed URL TTL must be positive and at most 7 days.");
        }
        try {
            BlobClient blob = containerClient.getBlobClient(photoKey);
            if (!blob.exists()) {
                throw new PhotoNotFoundException(photoKey);
            }
            BlobServiceSasSignatureValues sasValues = new BlobServiceSasSignatureValues(
                    OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(ttlSeconds),
                    new BlobSasPermission().setReadPermission(true)
            );
            String sas = blob.generateSas(sasValues);
            return URI.create(blob.getBlobUrl() + "?" + sas);
        } catch (BlobStorageException exception) {
            if (BlobErrorCode.BLOB_NOT_FOUND.equals(exception.getErrorCode())) {
                throw new PhotoNotFoundException(photoKey, exception);
            }
            throw new PhotoStorageException("Could not sign photo URL.", exception);
        } catch (AzureException exception) {
            throw new PhotoStorageException("Could not sign photo URL.", exception);
        }
    }

    @Override
    public void delete(String photoKey) {
        try {
            BlobClient blob = containerClient.getBlobClient(photoKey);
            blob.deleteIfExists();
        } catch (AzureException exception) {
            throw new PhotoStorageException("Could not delete photo.", exception);
        }
    }

    private void ensureContainerExists() {
        try {
            if (!containerClient.exists()) {
                containerClient.create();
            }
        } catch (AzureException exception) {
            throw new PhotoStorageException("Could not prepare photo container.", exception);
        }
    }
}
