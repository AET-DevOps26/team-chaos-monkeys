package com.foundflow.pickup.dto;

import com.foundflow.pickup.domain.ScheduleRecurrenceType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record PickupScheduleResponse(
        UUID id,
        ScheduleRecurrenceType recurrenceType,
        LocalDate startDate,
        LocalDate endDate,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        int slotLengthInMinutes,
        UUID venueId
) {
}
