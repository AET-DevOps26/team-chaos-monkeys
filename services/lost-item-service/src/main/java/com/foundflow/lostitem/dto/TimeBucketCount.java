package com.foundflow.lostitem.dto;

import java.time.LocalDate;

public record TimeBucketCount(
        LocalDate bucketStart,
        long count
) {
}
