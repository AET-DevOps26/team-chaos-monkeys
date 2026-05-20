package com.foundflow.founditem.dto;

import java.time.LocalDate;

public record TimeBucketCount(
        LocalDate bucketStart,
        long count
) {
}
