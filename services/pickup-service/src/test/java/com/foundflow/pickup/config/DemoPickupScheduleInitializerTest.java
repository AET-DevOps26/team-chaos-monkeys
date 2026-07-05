package com.foundflow.pickup.config;

import com.foundflow.pickup.domain.PickupSchedule;
import com.foundflow.pickup.domain.ScheduleRecurrenceType;
import com.foundflow.pickup.repository.PickupScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static com.foundflow.pickup.config.DemoPickupScheduleInitializer.DEMO_VENUE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoPickupScheduleInitializerTest {

    @Mock
    private PickupScheduleRepository scheduleRepository;

    private final DemoPickupScheduleInitializer initializer = new DemoPickupScheduleInitializer();

    @Test
    void seedsWeeklyDemoSchedulesWhenEnabledAndEmpty() throws Exception {
        when(scheduleRepository.findByVenueIdOrderByStartDateAscStartTimeAsc(DEMO_VENUE_ID))
                .thenReturn(List.of());

        initializer.seedDemoPickupSchedules(scheduleRepository, true).run();

        ArgumentCaptor<PickupSchedule> captor = ArgumentCaptor.forClass(PickupSchedule.class);
        verify(scheduleRepository, times(3)).save(captor.capture());

        List<PickupSchedule> saved = captor.getAllValues();
        assertThat(saved)
                .allSatisfy(s -> {
                    assertThat(s.getRecurrenceType()).isEqualTo(ScheduleRecurrenceType.WEEKLY);
                    assertThat(s.getVenueId()).isEqualTo(DEMO_VENUE_ID);
                    assertThat(s.getEndDate()).isNull();
                    assertThat(s.getStartTime()).isEqualTo(LocalTime.of(14, 0));
                    assertThat(s.getEndTime()).isEqualTo(LocalTime.of(17, 0));
                    assertThat(s.getSlotLengthInMinutes()).isEqualTo(30);
                });
        assertThat(saved).extracting(PickupSchedule::getDayOfWeek)
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
    }

    @Test
    void seedsNothingWhenDisabled() throws Exception {
        initializer.seedDemoPickupSchedules(scheduleRepository, false).run();

        verifyNoInteractions(scheduleRepository);
    }

    @Test
    void isIdempotentWhenSchedulesAlreadyExist() throws Exception {
        when(scheduleRepository.findByVenueIdOrderByStartDateAscStartTimeAsc(DEMO_VENUE_ID))
                .thenReturn(List.of(new PickupSchedule()));

        initializer.seedDemoPickupSchedules(scheduleRepository, true).run();

        verify(scheduleRepository, never()).save(any());
    }
}
