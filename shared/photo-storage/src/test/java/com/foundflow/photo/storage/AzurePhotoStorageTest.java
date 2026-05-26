package com.foundflow.photo.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AzurePhotoStorageTest {

    @Test
    void signedUrlChecksBlobExistsAndReturnsSasUrl() {
        BlobContainerClient containerClient = mock(BlobContainerClient.class);
        BlobClient blob = mock(BlobClient.class);
        when(containerClient.exists()).thenReturn(true);
        when(containerClient.getBlobClient("found-items/2026/05/photo-123.jpg")).thenReturn(blob);
        when(blob.exists()).thenReturn(true);
        when(blob.getBlobUrl())
                .thenReturn("https://account.blob.core.windows.net/foundflow-found-photos/photo-123.jpg");
        when(blob.generateSas(any(BlobServiceSasSignatureValues.class))).thenReturn("sv=test&sig=abc");

        AzurePhotoStorage storage = new AzurePhotoStorage(containerClient, "found-items");

        URI url = storage.signedUrl("found-items/2026/05/photo-123.jpg", Duration.ofMinutes(10));

        assertThat(url).hasToString(
                "https://account.blob.core.windows.net/foundflow-found-photos/photo-123.jpg?sv=test&sig=abc"
        );
        verify(blob).exists();
        verify(blob).generateSas(any(BlobServiceSasSignatureValues.class));
    }

    @Test
    void signedUrlRejectsInvalidTtl() {
        BlobContainerClient containerClient = mock(BlobContainerClient.class);
        when(containerClient.exists()).thenReturn(true);
        AzurePhotoStorage storage = new AzurePhotoStorage(containerClient, "found-items");

        assertThatThrownBy(() -> storage.signedUrl("found-items/2026/05/photo-123.jpg", Duration.ZERO))
                .isInstanceOf(PhotoStorageException.class)
                .hasMessageContaining("TTL must be positive");
    }
}
