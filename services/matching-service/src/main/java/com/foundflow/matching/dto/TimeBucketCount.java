package com.foundflow.matching.dto;

import java.time.LocalDate;

public record TimeBucketCount(
        LocalDate bucketStart,
        long count
) {
}
