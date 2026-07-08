package com.foundflow.photo.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemPhotoStorageTest {

    @Test
    void storeWritesFileAndReturnsParseableKey(@TempDir Path tempDir) throws Exception {
        FileSystemPhotoStorage storage = new FileSystemPhotoStorage(tempDir, "found-items");

        byte[] payload = "hello-photo".getBytes();
        String key = storage.store(new PhotoData(
                new ByteArrayInputStream(payload),
                "image/jpeg",
                payload.length
        ));

        assertThat(key).matches("found-items/[0-9]{4}/[0-9]{2}/[0-9a-f-]{36}\\.jpg");
        Path expectedPath = tempDir.resolve(key);
        assertThat(Files.exists(expectedPath)).isTrue();
        assertThat(Files.readAllBytes(expectedPath)).isEqualTo(payload);
    }

    @Test
    void retrieveReturnsStoredBytesAndContentType(@TempDir Path tempDir) throws Exception {
        FileSystemPhotoStorage storage = new FileSystemPhotoStorage(tempDir, "found-items");

        byte[] payload = "another".getBytes();
        String key = storage.store(new PhotoData(
                new ByteArrayInputStream(payload),
                "image/png",
                payload.length
        ));

        PhotoData retrieved = storage.retrieve(key);
        assertThat(retrieved.contentType()).isEqualTo("image/png");
        assertThat(retrieved.sizeBytes()).isEqualTo(payload.length);
        assertThat(retrieved.content().readAllBytes()).isEqualTo(payload);
    }

    @Test
    void retrieveThrowsPhotoNotFoundForUnknownKey(@TempDir Path tempDir) {
        FileSystemPhotoStorage storage = new FileSystemPhotoStorage(tempDir, "found-items");

        assertThatThrownBy(() -> storage.retrieve("found-items/2026/05/missing.jpg"))
                .isInstanceOf(PhotoNotFoundException.class);
    }

    @Test
    void deleteIsIdempotent(@TempDir Path tempDir) {
        FileSystemPhotoStorage storage = new FileSystemPhotoStorage(tempDir, "found-items");

        storage.delete("found-items/2026/05/never-existed.jpg");
        storage.delete("found-items/2026/05/never-existed.jpg");
    }

    @Test
    void signedUrlThrowsUnsupportedForStoredPhoto(@TempDir Path tempDir) {
        FileSystemPhotoStorage storage = new FileSystemPhotoStorage(tempDir, "found-items");

        byte[] payload = "bytes".getBytes();
        String key = storage.store(new PhotoData(
                new ByteArrayInputStream(payload),
                "image/jpeg",
                payload.length
        ));

        assertThatThrownBy(() -> storage.signedUrl(key, Duration.ofMinutes(5)))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("filesystem storage");
    }

    @Test
    void signedUrlThrowsForMissingPhoto(@TempDir Path tempDir) {
        FileSystemPhotoStorage storage = new FileSystemPhotoStorage(tempDir, "found-items");

        assertThatThrownBy(() -> storage.signedUrl("found-items/2026/05/missing.jpg", Duration.ofMinutes(5)))
                .isInstanceOf(PhotoNotFoundException.class);
    }

    @Test
    void pathTraversalIsRejected(@TempDir Path tempDir) {
        FileSystemPhotoStorage storage = new FileSystemPhotoStorage(tempDir, "found-items");

        assertThatThrownBy(() -> storage.retrieve("../escaped.jpg"))
                .isInstanceOf(PhotoStorageException.class)
                .hasMessageContaining("Invalid photo key");
    }
}
