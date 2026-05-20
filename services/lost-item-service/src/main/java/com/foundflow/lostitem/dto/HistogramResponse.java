package com.foundflow.lostitem.dto;

import java.util.List;

public record HistogramResponse(
        List<TimeBucketCount> perDay,
        List<TimeBucketCount> perWeek,
        List<TimeBucketCount> perMonth
) {
}
