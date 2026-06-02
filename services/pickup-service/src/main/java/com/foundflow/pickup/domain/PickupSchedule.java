package com.foundflow.pickup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "pickup_schedules")
public class PickupSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_type", nullable = false)
    private ScheduleRecurrenceType recurrenceType = ScheduleRecurrenceType.ONCE;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week")
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "slot_length_minutes", nullable = false)
    private int slotLengthInMinutes;

    @Column(name = "venue_id", nullable = false)
    private UUID venueId;

    public PickupSchedule() {
    }

    public PickupSchedule(
            LocalDate startDate,
            LocalTime startTime,
            LocalTime endTime,
            int slotLengthInMinutes,
            UUID venueId
    ) {
        this.recurrenceType = ScheduleRecurrenceType.ONCE;
        this.startDate = startDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.slotLengthInMinutes = slotLengthInMinutes;
        this.venueId = venueId;
    }

    public PickupSchedule(
            ScheduleRecurrenceType recurrenceType,
            LocalDate startDate,
            LocalDate endDate,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            int slotLengthInMinutes,
            UUID venueId
    ) {
        this.recurrenceType = recurrenceType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.slotLengthInMinutes = slotLengthInMinutes;
        this.venueId = venueId;
    }

    public UUID getId() {
        return id;
    }

    public ScheduleRecurrenceType getRecurrenceType() {
        return recurrenceType;
    }

    public void setRecurrenceType(ScheduleRecurrenceType recurrenceType) {
        this.recurrenceType = recurrenceType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public int getSlotLengthInMinutes() {
        return slotLengthInMinutes;
    }

    public void setSlotLengthInMinutes(int slotLengthInMinutes) {
        this.slotLengthInMinutes = slotLengthInMinutes;
    }

    public UUID getVenueId() {
        return venueId;
    }

    public void setVenueId(UUID venueId) {
        this.venueId = venueId;
    }
}
