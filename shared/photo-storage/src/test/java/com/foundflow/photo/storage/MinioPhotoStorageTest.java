package com.foundflow.photo.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinioPhotoStorageTest {

    @Test
    void signedUrlChecksObjectExistsAndReturnsPresignedUrl() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        MinioClient presignClient = mock(MinioClient.class);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(presignClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:9000/foundflow-found-photos/photo-123?X-Amz-Signature=test");

        MinioPhotoStorage storage = new MinioPhotoStorage(
                minioClient,
                presignClient,
                "foundflow-found-photos",
                "found-items"
        );

        URI url = storage.signedUrl("found-items/2026/05/photo-123.jpg", Duration.ofMinutes(10));

        assertThat(url).hasToString(
                "http://localhost:9000/foundflow-found-photos/photo-123?X-Amz-Signature=test"
        );
        verify(minioClient).statObject(any(StatObjectArgs.class));
        verify(presignClient).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    }

    @Test
    void signedUrlRejectsInvalidTtl() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        MinioClient presignClient = mock(MinioClient.class);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        MinioPhotoStorage storage = new MinioPhotoStorage(
                minioClient,
                presignClient,
                "foundflow-found-photos",
                "found-items"
        );

        assertThatThrownBy(() -> storage.signedUrl("found-items/2026/05/photo-123.jpg", Duration.ZERO))
                .isInstanceOf(PhotoStorageException.class)
                .hasMessageContaining("TTL must be positive");
    }
}
