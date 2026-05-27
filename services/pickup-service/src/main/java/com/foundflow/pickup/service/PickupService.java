package com.foundflow.pickup.service;

import com.foundflow.pickup.domain.Pickup;
import com.foundflow.pickup.domain.PickupEmailLog;
import com.foundflow.pickup.domain.PickupSchedule;
import com.foundflow.pickup.domain.ScheduleRecurrenceType;
import com.foundflow.pickup.dto.CreatePickupRequest;
import com.foundflow.pickup.dto.CreatePickupScheduleRequest;
import com.foundflow.pickup.dto.PickupEmailLogResponse;
import com.foundflow.pickup.dto.PickupResponse;
import com.foundflow.pickup.dto.PickupScheduleResponse;
import com.foundflow.pickup.dto.PickupSlotResponse;
import com.foundflow.pickup.dto.PublicPickupResponse;
import com.foundflow.pickup.dto.StaffPickupRequest;
import com.foundflow.pickup.dto.UpdatePickupRequest;
import com.foundflow.pickup.dto.UpdatePickupScheduleRequest;
import com.foundflow.pickup.repository.PickupEmailLogRepository;
import com.foundflow.pickup.repository.PickupRepository;
import com.foundflow.pickup.repository.PickupScheduleRepository;
import com.foundflow.pickup.security.VenueAccessService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class PickupService {

    private static final int PUBLIC_SLOT_LOOKAHEAD_DAYS = 30;

    private final PickupRepository pickupRepository;
    private final PickupScheduleRepository scheduleRepository;
    private final PickupEmailLogRepository emailLogRepository;
    private final VenueAccessService venueAccessService;
    private final MagicLinkService magicLinkService;
    private final PickupEmailSender emailSender;
    private final String publicBaseUrl;

    public PickupService(
            PickupRepository pickupRepository,
            PickupScheduleRepository scheduleRepository,
            PickupEmailLogRepository emailLogRepository,
            VenueAccessService venueAccessService,
            MagicLinkService magicLinkService,
            PickupEmailSender emailSender,
            @Value("${foundflow.public.base-url}") String publicBaseUrl
    ) {
        this.pickupRepository = pickupRepository;
        this.scheduleRepository = scheduleRepository;
        this.emailLogRepository = emailLogRepository;
        this.venueAccessService = venueAccessService;
        this.magicLinkService = magicLinkService;
        this.emailSender = emailSender;
        this.publicBaseUrl = publicBaseUrl;
    }

    public List<PickupSlotResponse> getPublicSlots(String token) {
        MagicLinkClaims claims = magicLinkService.verifyForSlots(token);
        return slotsForVenue(claims.venueId());
    }

    public PublicPickupResponse createPublicPickup(String token, CreatePickupRequest request) {
        MagicLinkClaims claims = magicLinkService.verify(token, MagicLinkService.TYPE_MATCH_VIEW);
        String email = normalizeEmail(request.email());
        if (claims.email() != null && !claims.email().equals(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Magic link email does not match request.");
        }

        ensureSlotAvailable(claims.venueId(), request.pickupAt(), null);
        Pickup pickup = pickupRepository.save(new Pickup(
                request.pickupAt(),
                claims.venueId(),
                claims.matchId(),
                email
        ));

        String manageToken = magicLinkService.createPickupManageToken(
                pickup.getId(),
                pickup.getMatchId(),
                pickup.getVenueId(),
                pickup.getEmail()
        );
        String manageUrl = publicBaseUrl + "/api/pickups/public/" + manageToken;
        emailSender.sendPickupScheduled(email, pickup.getVenueId(), manageUrl);

        return toPublicResponse(pickup, manageUrl);
    }

    public Optional<PublicPickupResponse> updatePublicPickup(String token, UpdatePickupRequest request) {
        MagicLinkClaims claims = magicLinkService.verify(token, MagicLinkService.TYPE_PICKUP_MANAGE);
        return pickupRepository.findById(claims.pickupId())
                .map(pickup -> {
                    verifyTokenOwnsPickup(claims, pickup);
                    String email = normalizeEmail(request.email());
                    if (claims.email() != null && !claims.email().equals(email)) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Magic link email does not match request.");
                    }
                    ensureSlotAvailable(pickup.getVenueId(), request.pickupAt(), pickup.getId());
                    pickup.setPickupAt(request.pickupAt());
                    pickup.setEmail(email);
                    Pickup updated = pickupRepository.save(pickup);
                    return toPublicResponse(updated, publicBaseUrl + "/api/pickups/public/" + token);
                });
    }

    public boolean deletePublicPickup(String token) {
        MagicLinkClaims claims = magicLinkService.verify(token, MagicLinkService.TYPE_PICKUP_MANAGE);
        return pickupRepository.findById(claims.pickupId())
                .map(pickup -> {
                    verifyTokenOwnsPickup(claims, pickup);
                    pickupRepository.delete(pickup);
                    return true;
                })
                .orElse(false);
    }

    public List<PickupResponse> getPickups(UUID requestedVenueId, Jwt jwt) {
        UUID venueId = resolveVenueFilter(requestedVenueId, jwt);
        List<Pickup> pickups = venueId == null ? pickupRepository.findAll() : pickupRepository.findByVenueId(venueId);
        return pickups.stream().map(this::toResponse).toList();
    }

    public Optional<PickupResponse> upsertPickupForMatch(UUID matchId, StaffPickupRequest request, Jwt jwt) {
        UUID venueId = resolveWriteVenue(request.venueId(), jwt);
        return Optional.of(pickupRepository.findFirstByMatchId(matchId)
                .map(existing -> {
                    verifyVenueAccess(jwt, existing.getVenueId());
                    ensureSlotAvailable(venueId, request.pickupAt(), existing.getId());
                    existing.setPickupAt(request.pickupAt());
                    existing.setVenueId(venueId);
                    existing.setEmail(normalizeEmail(request.email()));
                    return pickupRepository.save(existing);
                })
                .orElseGet(() -> {
                    ensureSlotAvailable(venueId, request.pickupAt(), null);
                    return pickupRepository.save(new Pickup(
                            request.pickupAt(),
                            venueId,
                            matchId,
                            normalizeEmail(request.email())
                    ));
                }))
                .map(this::toResponse);
    }

    public Optional<PickupResponse> updatePickup(UUID pickupId, StaffPickupRequest request, Jwt jwt) {
        return pickupRepository.findById(pickupId)
                .map(pickup -> {
                    verifyVenueAccess(jwt, pickup.getVenueId());
                    UUID venueId = resolveWriteVenue(request.venueId(), jwt);
                    ensureSlotAvailable(venueId, request.pickupAt(), pickup.getId());
                    pickup.setPickupAt(request.pickupAt());
                    pickup.setVenueId(venueId);
                    pickup.setMatchId(request.matchId() == null ? pickup.getMatchId() : request.matchId());
                    pickup.setEmail(normalizeEmail(request.email()));
                    return toResponse(pickupRepository.save(pickup));
                });
    }

    public boolean deletePickup(UUID pickupId, Jwt jwt) {
        return pickupRepository.findById(pickupId)
                .map(pickup -> {
                    verifyVenueAccess(jwt, pickup.getVenueId());
                    pickupRepository.delete(pickup);
                    return true;
                })
                .orElse(false);
    }

    public boolean deletePickupForMatch(UUID matchId, Jwt jwt) {
        return pickupRepository.findFirstByMatchId(matchId)
                .map(pickup -> {
                    verifyVenueAccess(jwt, pickup.getVenueId());
                    pickupRepository.delete(pickup);
                    return true;
                })
                .orElse(false);
    }

    public List<PickupScheduleResponse> getSchedules(UUID requestedVenueId, Jwt jwt) {
        UUID venueId = resolveVenueFilter(requestedVenueId, jwt);
        List<PickupSchedule> schedules = venueId == null
                ? scheduleRepository.findAll()
                : scheduleRepository.findByVenueIdOrderByStartDateAscStartTimeAsc(venueId);
        return schedules.stream().map(this::toScheduleResponse).toList();
    }

    public PickupScheduleResponse createSchedule(CreatePickupScheduleRequest request, Jwt jwt) {
        UUID venueId = resolveWriteVenue(request.venueId(), jwt);
        validateSchedule(
                recurrenceType(request.recurrenceType()),
                request.startDate(),
                request.endDate(),
                request.dayOfWeek(),
                request.startTime(),
                request.endTime()
        );
        PickupSchedule schedule = scheduleRepository.save(new PickupSchedule(
                recurrenceType(request.recurrenceType()),
                request.startDate(),
                weeklyEndDate(request.recurrenceType(), request.endDate()),
                weeklyDayOfWeek(request.recurrenceType(), request.dayOfWeek()),
                request.startTime(),
                request.endTime(),
                request.slotLengthInMinutes(),
                venueId
        ));
        return toScheduleResponse(schedule);
    }

    public Optional<PickupScheduleResponse> updateSchedule(
            UUID scheduleId,
            UpdatePickupScheduleRequest request,
            Jwt jwt
    ) {
        return scheduleRepository.findById(scheduleId)
                .map(schedule -> {
                    verifyVenueAccess(jwt, schedule.getVenueId());
                    UUID venueId = resolveWriteVenue(request.venueId(), jwt);
                    ScheduleRecurrenceType recurrenceType = recurrenceType(request.recurrenceType());
                    validateSchedule(
                            recurrenceType,
                            request.startDate(),
                            request.endDate(),
                            request.dayOfWeek(),
                            request.startTime(),
                            request.endTime()
                    );
                    schedule.setRecurrenceType(recurrenceType);
                    schedule.setStartDate(request.startDate());
                    schedule.setEndDate(weeklyEndDate(recurrenceType, request.endDate()));
                    schedule.setDayOfWeek(weeklyDayOfWeek(recurrenceType, request.dayOfWeek()));
                    schedule.setStartTime(request.startTime());
                    schedule.setEndTime(request.endTime());
                    schedule.setSlotLengthInMinutes(request.slotLengthInMinutes());
                    schedule.setVenueId(venueId);
                    return toScheduleResponse(scheduleRepository.save(schedule));
                });
    }

    public boolean deleteSchedule(UUID scheduleId, Jwt jwt) {
        return scheduleRepository.findById(scheduleId)
                .map(schedule -> {
                    verifyVenueAccess(jwt, schedule.getVenueId());
                    scheduleRepository.delete(schedule);
                    return true;
                })
                .orElse(false);
    }

    public List<PickupEmailLogResponse> getEmailLog(String recipient, Jwt jwt) {
        List<PickupEmailLog> emailLogs;
        if (venueAccessService.isAdmin(jwt)) {
            emailLogs = recipient == null || recipient.isBlank()
                    ? emailLogRepository.findAll()
                    : emailLogRepository.findByRecipientOrderBySentAtDesc(normalizeEmail(recipient));
        } else {
            UUID venueId = venueAccessService.getVenueId(jwt);
            emailLogs = recipient == null || recipient.isBlank()
                    ? emailLogRepository.findByVenueIdOrderBySentAtDesc(venueId)
                    : emailLogRepository.findByVenueIdAndRecipientOrderBySentAtDesc(
                            venueId,
                            normalizeEmail(recipient)
                    );
        }
        return emailLogs.stream()
                .map(this::toEmailLogResponse)
                .toList();
    }

    private List<PickupSlotResponse> slotsForVenue(UUID venueId) {
        List<PickupSchedule> schedules = scheduleRepository.findByVenueIdOrderByStartDateAscStartTimeAsc(venueId);
        Set<LocalDateTime> occupied = new HashSet<>();
        for (PickupSchedule schedule : schedules) {
            for (LocalDate date : scheduleDates(schedule)) {
                LocalDateTime dayStart = date.atStartOfDay();
                pickupRepository.findByVenueIdAndPickupAtBetween(venueId, dayStart, dayStart.plusDays(1))
                        .forEach(pickup -> occupied.add(pickup.getPickupAt()));
            }
        }

        return schedules.stream()
                .flatMap(schedule -> buildSlots(schedule, occupied).stream())
                .toList();
    }

    private List<LocalDate> scheduleDates(PickupSchedule schedule) {
        if (schedule.getRecurrenceType() == ScheduleRecurrenceType.WEEKLY) {
            return weeklyDates(schedule);
        }
        return List.of(schedule.getStartDate());
    }

    private List<LocalDate> weeklyDates(PickupSchedule schedule) {
        LocalDate today = LocalDate.now();
        LocalDate from = schedule.getStartDate().isAfter(today) ? schedule.getStartDate() : today;
        LocalDate latest = from.plusDays(PUBLIC_SLOT_LOOKAHEAD_DAYS);
        LocalDate until = schedule.getEndDate() != null && schedule.getEndDate().isBefore(latest)
                ? schedule.getEndDate()
                : latest;
        LocalDate cursor = from.with(TemporalAdjusters.nextOrSame(schedule.getDayOfWeek()));

        java.util.ArrayList<LocalDate> dates = new java.util.ArrayList<>();
        while (!cursor.isAfter(until)) {
            dates.add(cursor);
            cursor = cursor.plusWeeks(1);
        }
        return dates;
    }

    private List<PickupSlotResponse> buildSlots(PickupSchedule schedule, Set<LocalDateTime> occupied) {
        java.util.ArrayList<PickupSlotResponse> slots = new java.util.ArrayList<>();
        for (LocalDate date : scheduleDates(schedule)) {
            slots.addAll(buildSlotsForDate(schedule, date, occupied));
        }
        return slots;
    }

    private List<PickupSlotResponse> buildSlotsForDate(
            PickupSchedule schedule,
            LocalDate date,
            Set<LocalDateTime> occupied
    ) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime cursor = dayStart.toLocalDate().atTime(schedule.getStartTime());
        LocalDateTime end = dayStart.toLocalDate().atTime(schedule.getEndTime());
        int minutes = schedule.getSlotLengthInMinutes();
        java.util.ArrayList<PickupSlotResponse> slots = new java.util.ArrayList<>();
        while (!cursor.plusMinutes(minutes).isAfter(end)) {
            slots.add(new PickupSlotResponse(
                    cursor,
                    cursor.plusMinutes(minutes),
                    !occupied.contains(cursor)
            ));
            cursor = cursor.plusMinutes(minutes);
        }
        return slots;
    }

    private void ensureSlotAvailable(UUID venueId, LocalDateTime pickupAt, UUID currentPickupId) {
        boolean slotExists = slotsForVenue(venueId).stream()
                .anyMatch(slot -> slot.startsAt().equals(pickupAt));
        if (!slotExists) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pickup time is not part of the venue schedule.");
        }

        LocalDateTime dayStart = pickupAt.toLocalDate().atStartOfDay();
        boolean occupied = pickupRepository.findByVenueIdAndPickupAtBetween(venueId, dayStart, dayStart.plusDays(1))
                .stream()
                .anyMatch(pickup -> pickup.getPickupAt().equals(pickupAt)
                        && !Objects.equals(pickup.getId(), currentPickupId));
        if (occupied) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pickup slot is already booked.");
        }
    }

    private UUID resolveVenueFilter(UUID requestedVenueId, Jwt jwt) {
        if (venueAccessService.isAdmin(jwt)) {
            return requestedVenueId;
        }

        UUID jwtVenueId = venueAccessService.getVenueId(jwt);
        if (requestedVenueId != null && !requestedVenueId.equals(jwtVenueId)) {
            throw new AccessDeniedException("No access to this venue.");
        }
        return jwtVenueId;
    }

    private UUID resolveWriteVenue(UUID requestedVenueId, Jwt jwt) {
        if (venueAccessService.isAdmin(jwt)) {
            if (requestedVenueId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "venueId is required for admins.");
            }
            return requestedVenueId;
        }
        return venueAccessService.getVenueId(jwt);
    }

    private void verifyVenueAccess(Jwt jwt, UUID venueId) {
        if (!venueAccessService.canAccessVenue(jwt, venueId)) {
            throw new AccessDeniedException("No access to this venue.");
        }
    }

    private void verifyTokenOwnsPickup(MagicLinkClaims claims, Pickup pickup) {
        if (!pickup.getId().equals(claims.pickupId())
                || !pickup.getMatchId().equals(claims.matchId())
                || !pickup.getVenueId().equals(claims.venueId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Magic link does not belong to this pickup.");
        }
    }

    private void validateSchedule(
            ScheduleRecurrenceType recurrenceType,
            LocalDate startDate,
            LocalDate endDate,
            java.time.DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime
    ) {
        if (!startTime.isBefore(endTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Schedule startTime must be before endTime.");
        }
        if (startDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate is required for pickup schedules.");
        }
        if (recurrenceType == ScheduleRecurrenceType.WEEKLY) {
            if (dayOfWeek == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "dayOfWeek is required for weekly schedules."
                );
            }
            if (endDate != null && endDate.isBefore(startDate)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must be on or after startDate.");
            }
        }
    }

    private ScheduleRecurrenceType recurrenceType(ScheduleRecurrenceType recurrenceType) {
        return recurrenceType == null ? ScheduleRecurrenceType.ONCE : recurrenceType;
    }

    private LocalDate weeklyEndDate(ScheduleRecurrenceType recurrenceType, LocalDate endDate) {
        return recurrenceType(recurrenceType) == ScheduleRecurrenceType.WEEKLY ? endDate : null;
    }

    private java.time.DayOfWeek weeklyDayOfWeek(
            ScheduleRecurrenceType recurrenceType,
            java.time.DayOfWeek dayOfWeek
    ) {
        return recurrenceType(recurrenceType) == ScheduleRecurrenceType.WEEKLY ? dayOfWeek : null;
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private PickupResponse toResponse(Pickup pickup) {
        return new PickupResponse(
                pickup.getId(),
                pickup.getPickupAt(),
                pickup.getVenueId(),
                pickup.getMatchId(),
                pickup.getEmail()
        );
    }

    private PublicPickupResponse toPublicResponse(Pickup pickup, String manageUrl) {
        return new PublicPickupResponse(
                pickup.getId(),
                pickup.getPickupAt(),
                pickup.getVenueId(),
                pickup.getMatchId(),
                pickup.getEmail(),
                manageUrl
        );
    }

    private PickupScheduleResponse toScheduleResponse(PickupSchedule schedule) {
        return new PickupScheduleResponse(
                schedule.getId(),
                schedule.getRecurrenceType(),
                schedule.getStartDate(),
                schedule.getEndDate(),
                schedule.getDayOfWeek(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getSlotLengthInMinutes(),
                schedule.getVenueId()
        );
    }

    private PickupEmailLogResponse toEmailLogResponse(PickupEmailLog emailLog) {
        return new PickupEmailLogResponse(
                emailLog.getId(),
                emailLog.getRecipient(),
                emailLog.getVenueId(),
                emailLog.getSubject(),
                emailLog.getBody(),
                emailLog.getMagicLink(),
                emailLog.getSentAt()
        );
    }
}
