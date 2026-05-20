package com.foundflow.matching.dto;

import java.util.List;

public record HistogramResponse(
        List<TimeBucketCount> perDay,
        List<TimeBucketCount> perWeek,
        List<TimeBucketCount> perMonth
) {
}
