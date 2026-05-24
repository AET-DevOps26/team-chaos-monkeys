package com.foundflow.photo.storage;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhotoKeyFactoryTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-07T11:22:33Z"),
            ZoneOffset.UTC
    );

    @Test
    void createKey_usesDomainYearMonthAndKnownExtension() {
        PhotoKeyFactory factory = new PhotoKeyFactory("found-items", FIXED_CLOCK);

        String key = factory.createKey("image/jpeg");

        assertThat(key).matches("found-items/2026/05/[0-9a-f-]{36}\\.jpg");
    }

    @Test
    void createKey_mapsPngAndWebpExtensions() {
        PhotoKeyFactory factory = new PhotoKeyFactory("lost-reports", FIXED_CLOCK);

        assertThat(factory.createKey("image/png")).endsWith(".png");
        assertThat(factory.createKey("image/webp")).endsWith(".webp");
    }

    @Test
    void createKey_throwsForUnsupportedContentType() {
        PhotoKeyFactory factory = new PhotoKeyFactory("found-items", FIXED_CLOCK);

        assertThatThrownBy(() -> factory.createKey("image/heic"))
                .isInstanceOf(PhotoStorageException.class)
                .hasMessageContaining("image/heic");
    }

    @Test
    void contentTypeFor_resolvesAllowedExtensions() {
        PhotoKeyFactory factory = new PhotoKeyFactory("found-items", FIXED_CLOCK);

        assertThat(factory.contentTypeFor("found-items/2026/05/x.jpg")).isEqualTo("image/jpeg");
        assertThat(factory.contentTypeFor("found-items/2026/05/x.jpeg")).isEqualTo("image/jpeg");
        assertThat(factory.contentTypeFor("found-items/2026/05/x.png")).isEqualTo("image/png");
        assertThat(factory.contentTypeFor("found-items/2026/05/x.webp")).isEqualTo("image/webp");
    }

    @Test
    void contentTypeFor_throwsOnUnknownExtension() {
        PhotoKeyFactory factory = new PhotoKeyFactory("found-items", FIXED_CLOCK);

        assertThatThrownBy(() -> factory.contentTypeFor("found-items/2026/05/x.exe"))
                .isInstanceOf(PhotoStorageException.class);
    }
}
