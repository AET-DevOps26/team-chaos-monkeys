package com.foundflow.pickup.config;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.foundflow.pickup.domain.PickupSchedule;
import com.foundflow.pickup.domain.ScheduleRecurrenceType;
import com.foundflow.pickup.repository.PickupScheduleRepository;

/**
 * Seeds recurring pickup schedules for the demo venue so the local stack ships with
 * bookable slots (match → guest confirm → pick a slot); without it the demo pickup
 * flow dead-ends at "no slots available". Enabled only when {@code SEED_DEMO_DATA=true}
 * and skipped once the demo venue already has schedules, so it is safe to re-run.
 */
@Configuration
public class DemoPickupScheduleInitializer {

    // Matches the fixed id seeded by operations-service's DemoVenueInitializer.
    static final UUID DEMO_VENUE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final List<DayOfWeek> DEMO_DAYS =
            List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
    private static final LocalTime WINDOW_START = LocalTime.of(14, 0);
    private static final LocalTime WINDOW_END = LocalTime.of(17, 0);
    private static final int SLOT_LENGTH_MINUTES = 30;

    @Bean
    public CommandLineRunner seedDemoPickupSchedules(
            PickupScheduleRepository scheduleRepository,
            @Value("${SEED_DEMO_DATA:false}") boolean seedDemoData) {
        return args -> {
            if (!seedDemoData) {
                return;
            }
            if (!scheduleRepository
                    .findByVenueIdOrderByStartDateAscStartTimeAsc(DEMO_VENUE_ID)
                    .isEmpty()) {
                return;
            }

            // Evergreen weekly slots: no end date, start today, so they stay bookable
            // as the demo date drifts (weeklyDates() generates up to the lookahead horizon).
            LocalDate today = LocalDate.now();
            for (DayOfWeek day : DEMO_DAYS) {
                scheduleRepository.save(new PickupSchedule(
                        ScheduleRecurrenceType.WEEKLY,
                        today,
                        null,
                        day,
                        WINDOW_START,
                        WINDOW_END,
                        SLOT_LENGTH_MINUTES,
                        DEMO_VENUE_ID));
            }
        };
    }
}
