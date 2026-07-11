package com.foundflow.pickup.service;

import com.foundflow.magiclink.MagicLinkClaims;
import com.foundflow.magiclink.MagicLinkService;
import com.foundflow.pickup.domain.Pickup;
import com.foundflow.pickup.domain.PickupSchedule;
import com.foundflow.pickup.domain.ScheduleRecurrenceType;
import com.foundflow.pickup.dto.CreatePickupRequest;
import com.foundflow.pickup.dto.CreatePickupScheduleRequest;
import com.foundflow.pickup.dto.PickupSlotResponse;
import com.foundflow.pickup.messaging.PickupConfirmationEventPublisher;
import com.foundflow.pickup.messaging.PickupScheduledEventPublisher;
import com.foundflow.pickup.repository.PickupRepository;
import com.foundflow.pickup.repository.PickupScheduleRepository;
import com.foundflow.pickup.security.VenueAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PickupServiceTest {

    @Mock
    private PickupRepository pickupRepository;

    @Mock
    private PickupScheduleRepository scheduleRepository;

    @Mock
    private MagicLinkService magicLinkService;

    @Mock
    private PickupConfirmationEventPublisher confirmationEventPublisher;

    @Mock
    private PickupScheduledEventPublisher scheduledEventPublisher;

    private final VenueAccessService venueAccessService = new VenueAccessService();

    @Test
    void getPublicSlots_shouldExpandScheduleIntoBookableSlots() {
        UUID venueId = UUID.randomUUID();
        String token = "match-token";
        PickupService service = pickupService();
        when(magicLinkService.verifyForSlots(token))
                .thenReturn(new MagicLinkClaims("match_view", UUID.randomUUID(), null, venueId, "lost@example.com", 1L));
        when(scheduleRepository.findByVenueIdOrderByStartDateAscStartTimeAsc(venueId))
                .thenReturn(List.of(schedule(venueId)));
        when(pickupRepository.findByVenueIdAndPickupAtBetween(
                venueId,
                LocalDate.of(2026, 6, 1).atStartOfDay(),
                LocalDate.of(2026, 6, 2).atStartOfDay()
        )).thenReturn(List.of(new Pickup(
                LocalDateTime.of(2026, 6, 1, 9, 30),
                venueId,
                UUID.randomUUID(),
                "person@example.com"
        )));

        List<PickupSlotResponse> slots = service.getPublicSlots(token);

        assertEquals(2, slots.size());
        assertEquals(LocalDateTime.of(2026, 6, 1, 9, 0), slots.get(0).startsAt());
        assertTrue(slots.get(0).available());
        assertEquals(LocalDateTime.of(2026, 6, 1, 9, 30), slots.get(1).startsAt());
        assertEquals(false, slots.get(1).available());
    }

    @Test
    void createPublicPickup_shouldPersistPickupAndPublishConfirmation() {
        UUID venueId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        String token = "match-token";
        PickupService service = pickupService();
        when(magicLinkService.verify(token, MagicLinkService.TYPE_MATCH_VIEW))
                .thenReturn(new MagicLinkClaims("match_view", matchId, null, venueId, "lost@example.com", 1L));
        when(scheduleRepository.findByVenueIdOrderByStartDateAscStartTimeAsc(venueId))
                .thenReturn(List.of(schedule(venueId)));
        when(pickupRepository.save(any(Pickup.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(magicLinkService.createPickupManageToken(null, matchId, venueId, "lost@example.com"))
                .thenReturn("manage-token");

        var response = service.createPublicPickup(
                token,
                new CreatePickupRequest(LocalDateTime.of(2026, 6, 1, 9, 0), "Lost@Example.com")
        );

        assertEquals(matchId, response.matchId());
        assertEquals("lost@example.com", response.email());
        assertTrue(response.manageUrl().endsWith("/api/pickups/public/manage-token"));
        verify(confirmationEventPublisher).publishPickupConfirmationRequested(
                null,
                matchId,
                "lost@example.com",
                venueId,
                LocalDateTime.of(2026, 6, 1, 9, 0)
        );
        // Retires the booked found item from matching (issue #367).
        verify(scheduledEventPublisher).publishPickupScheduled(null, matchId, venueId);
    }

    @Test
    void getPublicSlots_shouldExpandWeeklySchedule() {
        UUID venueId = UUID.randomUUID();
        String token = "match-token";
        LocalDate firstMonday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        PickupService service = pickupService();
        when(magicLinkService.verifyForSlots(token))
                .thenReturn(new MagicLinkClaims("match_view", UUID.randomUUID(), null, venueId, "lost@example.com", 1L));
        when(scheduleRepository.findByVenueIdOrderByStartDateAscStartTimeAsc(venueId))
                .thenReturn(List.of(new PickupSchedule(
                        ScheduleRecurrenceType.WEEKLY,
                        firstMonday,
                        firstMonday.plusWeeks(1),
                        DayOfWeek.MONDAY,
                        LocalTime.of(9, 0),
                        LocalTime.of(10, 0),
                        30,
                        venueId
                )));
        when(pickupRepository.findByVenueIdAndPickupAtBetween(any(), any(), any()))
                .thenReturn(List.of());

        List<PickupSlotResponse> slots = service.getPublicSlots(token);

        assertEquals(4, slots.size());
        assertEquals(firstMonday.atTime(9, 0), slots.get(0).startsAt());
        assertEquals(firstMonday.plusWeeks(1).atTime(9, 30), slots.get(3).startsAt());
        assertTrue(slots.stream().allMatch(PickupSlotResponse::available));
    }

    @Test
    void createSchedule_shouldUseStaffVenue() {
        UUID venueId = UUID.randomUUID();
        PickupService service = pickupService();
        when(scheduleRepository.save(any(PickupSchedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createSchedule(
                new CreatePickupScheduleRequest(
                        null,
                        LocalDate.of(2026, 6, 1),
                        null,
                        null,
                        LocalTime.of(9, 0),
                        LocalTime.of(10, 0),
                        30,
                        UUID.randomUUID()
                ),
                staffJwt(venueId)
        );

        assertEquals(venueId, response.venueId());
    }

    private PickupService pickupService() {
        return new PickupService(
                pickupRepository,
                scheduleRepository,
                venueAccessService,
                magicLinkService,
                confirmationEventPublisher,
                scheduledEventPublisher,
                "http://localhost:8080"
        );
    }

    private PickupSchedule schedule(UUID venueId) {
        return new PickupSchedule(
                LocalDate.of(2026, 6, 1),
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                30,
                venueId
        );
    }

    private Jwt staffJwt(UUID venueId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("roles", List.of("STAFF"))
                .claim("venue_id", venueId.toString())
                .build();
    }
}
