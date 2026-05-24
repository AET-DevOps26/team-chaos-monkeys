package com.foundflow.photo.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhotoConstraintsTest {

    @Test
    void check_acceptsValidPhoto() {
        assertThat(PhotoConstraints.check("image/jpeg", 1_000_000L)).isNull();
        assertThat(PhotoConstraints.check("image/png", 5_000_000L)).isNull();
        assertThat(PhotoConstraints.check("image/webp", PhotoConstraints.MAX_PHOTO_SIZE_BYTES)).isNull();
    }

    @Test
    void check_rejectsZeroOrNegativeSize() {
        assertThat(PhotoConstraints.check("image/jpeg", 0L)).isEqualTo(PhotoConstraints.Violation.EMPTY);
        assertThat(PhotoConstraints.check("image/jpeg", -1L)).isEqualTo(PhotoConstraints.Violation.EMPTY);
    }

    @Test
    void check_rejectsOversizePhoto() {
        long oversize = PhotoConstraints.MAX_PHOTO_SIZE_BYTES + 1L;
        assertThat(PhotoConstraints.check("image/jpeg", oversize)).isEqualTo(PhotoConstraints.Violation.TOO_LARGE);
    }

    @Test
    void check_rejectsUnknownContentType() {
        assertThat(PhotoConstraints.check("image/heic", 1_000L))
                .isEqualTo(PhotoConstraints.Violation.UNSUPPORTED_TYPE);
        assertThat(PhotoConstraints.check(null, 1_000L))
                .isEqualTo(PhotoConstraints.Violation.UNSUPPORTED_TYPE);
    }

    @Test
    void emptyTakesPrecedenceOverContentType() {
        assertThat(PhotoConstraints.check(null, 0L)).isEqualTo(PhotoConstraints.Violation.EMPTY);
    }
}
