package com.foundflow.pickup.dto;

import com.foundflow.pickup.domain.ScheduleRecurrenceType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CreatePickupScheduleRequest(
        ScheduleRecurrenceType recurrenceType,

        @NotNull
        LocalDate startDate,

        LocalDate endDate,

        DayOfWeek dayOfWeek,

        @NotNull
        LocalTime startTime,

        @NotNull
        LocalTime endTime,

        @Min(1)
        int slotLengthInMinutes,

        UUID venueId
) {
}
