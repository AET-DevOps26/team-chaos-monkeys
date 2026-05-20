package com.foundflow.lostitem.service;

import com.foundflow.common.domain.ItemAttributes;
import com.foundflow.lostitem.domain.LostReport;
import com.foundflow.lostitem.domain.ReportStatus;
import com.foundflow.lostitem.dto.CreateLostReportRequest;
import com.foundflow.lostitem.dto.HistogramResponse;
import com.foundflow.lostitem.dto.ItemAttributesDto;
import com.foundflow.lostitem.dto.LostReportResponse;
import com.foundflow.lostitem.dto.TimeBucketCount;
import com.foundflow.lostitem.dto.UpdateLostReportRequest;
import com.foundflow.lostitem.repository.BucketCountView;
import com.foundflow.lostitem.repository.LostReportRepository;
import com.foundflow.lostitem.security.VenueAccessService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LostReportService {

    private final LostReportRepository lostReportRepository;
    private final VenueAccessService venueAccessService;

    public LostReportService(
            LostReportRepository lostReportRepository,
            VenueAccessService venueAccessService
    ) {
        this.lostReportRepository = lostReportRepository;
        this.venueAccessService = venueAccessService;
    }

    public LostReportResponse createLostReport(
            CreateLostReportRequest request,
            Jwt jwt
    ) {
        UUID venueId = resolveCreateVenueId(request, jwt);

        LostReport lostReport = new LostReport(
                request.photoKey(),
                request.description(),
                request.lostAt(),
                request.location(),
                ReportStatus.OPEN,
                venueId,
                request.contactEmail(),
                toItemAttributes(request.attributes())
        );

        LostReport savedLostReport = lostReportRepository.save(lostReport);
        return toResponse(savedLostReport);
    }

    private UUID resolveCreateVenueId(CreateLostReportRequest request, Jwt jwt) {
        if (jwt != null && !venueAccessService.isAdmin(jwt)) {
            return venueAccessService.getVenueId(jwt);
        }

        if (request.venueId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "venueId is required for public and admin lost item reports."
            );
        }

        return request.venueId();
    }

    public List<LostReportResponse> getAllLostReports(
            ReportStatus status,
            Jwt jwt
    ) {
        return findAccessibleLostReports(status, jwt)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public Optional<LostReportResponse> getLostReportById(
            UUID id,
            Jwt jwt
    ) {
        return lostReportRepository.findById(id)
                .map(lostReport -> {
                    verifyVenueAccess(jwt, lostReport.getVenueId());
                    return lostReport;
                })
                .map(this::toResponse);
    }

    public Optional<LostReportResponse> updateLostReport(
            UUID id,
            UpdateLostReportRequest request,
            Jwt jwt
    ) {
        return lostReportRepository.findById(id)
                .map(existingReport -> {
                    verifyVenueAccess(jwt, existingReport.getVenueId());

                    existingReport.setPhotoKey(request.photoKey());
                    existingReport.setDescription(request.description());
                    existingReport.setLostAt(request.lostAt());
                    existingReport.setLocation(request.location());
                    existingReport.setStatus(request.status());
                    if (venueAccessService.isAdmin(jwt)) {
                        if (request.venueId() == null) {
                            throw new ResponseStatusException(
                                    HttpStatus.BAD_REQUEST,
                                    "venueId is required when updating a lost report."
                            );
                        }
                        existingReport.setVenueId(request.venueId());
                    }
                    existingReport.setContactEmail(request.contactEmail());
                    existingReport.setAttributes(toItemAttributes(request.attributes()));

                    LostReport updatedReport = lostReportRepository.save(existingReport);
                    return toResponse(updatedReport);
                });
    }

    public long countLostReports(ReportStatus status, Jwt jwt) {
        return countLostReports(status, null, jwt);
    }

    public long countLostReports(ReportStatus status, UUID requestedVenueId, Jwt jwt) {
        UUID venueId = resolveVenueFilter(requestedVenueId, jwt);
        if (venueId != null) {
            if (status == null) {
                return lostReportRepository.countByVenueId(venueId);
            }

            return lostReportRepository.countByVenueIdAndStatus(venueId, status);
        }

        if (venueAccessService.isAdmin(jwt)) {
            if (status == null) {
                return lostReportRepository.count();
            }

            return lostReportRepository.countByStatus(status);
        }
        throw new AccessDeniedException("Missing venue access.");
    }

    public HistogramResponse getLostReportHistogram(ReportStatus status, Jwt jwt) {
        return getLostReportHistogram(status, null, jwt);
    }

    public HistogramResponse getLostReportHistogram(ReportStatus status, UUID requestedVenueId, Jwt jwt) {
        UUID venueId = resolveVenueFilter(requestedVenueId, jwt);
        List<TimeBucketCount> perDay = toTimeBucketCounts(
                lostReportRepository.findDailyBuckets(
                        venueId,
                        status == null ? null : status.name()
                )
        );

        return new HistogramResponse(
                perDay,
                aggregate(perDay, this::weekStart),
                aggregate(perDay, this::monthStart)
        );
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

    private List<LostReport> findAccessibleLostReports(ReportStatus status, Jwt jwt) {
        if (venueAccessService.isAdmin(jwt)) {
            if (status == null) {
                return lostReportRepository.findAll();
            }

            return lostReportRepository.findByStatus(status);
        }

        UUID venueId = venueAccessService.getVenueId(jwt);
        if (status == null) {
            return lostReportRepository.findByVenueId(venueId);
        }

        return lostReportRepository.findByVenueIdAndStatus(venueId, status);
    }

    private void verifyVenueAccess(Jwt jwt, UUID resourceVenueId) {
        if (!venueAccessService.canAccessVenue(jwt, resourceVenueId)) {
            throw new AccessDeniedException("No access to this venue.");
        }
    }

    private List<TimeBucketCount> toTimeBucketCounts(List<BucketCountView> buckets) {
        return buckets.stream()
                .map(bucket -> new TimeBucketCount(bucket.getBucketStart(), bucket.getCount()))
                .toList();
    }

    private List<TimeBucketCount> aggregate(
            List<TimeBucketCount> buckets,
            Function<LocalDate, LocalDate> bucketSelector
    ) {
        Map<LocalDate, Long> groupedBuckets = buckets.stream()
                .collect(Collectors.groupingBy(
                        bucket -> bucketSelector.apply(bucket.bucketStart()),
                        TreeMap::new,
                        Collectors.summingLong(TimeBucketCount::count)
                ));

        return groupedBuckets.entrySet()
                .stream()
                .map(entry -> new TimeBucketCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    private LocalDate weekStart(LocalDate date) {
        if (date == null) {
            return null;
        }

        return date.with(DayOfWeek.MONDAY);
    }

    private LocalDate monthStart(LocalDate date) {
        if (date == null) {
            return null;
        }

        return date.withDayOfMonth(1);
    }

    private ItemAttributes toItemAttributes(ItemAttributesDto dto) {
        if (dto == null) {
            return null;
        }

        return new ItemAttributes(
                dto.category(),
                dto.brand(),
                dto.color(),
                dto.marks()
        );
    }

    private ItemAttributesDto toItemAttributesDto(ItemAttributes attributes) {
        if (attributes == null) {
            return null;
        }

        return new ItemAttributesDto(
                attributes.getCategory(),
                attributes.getBrand(),
                attributes.getColor(),
                attributes.getMarks()
        );
    }

    private LostReportResponse toResponse(LostReport lostReport) {
        return new LostReportResponse(
                lostReport.getId(),
                lostReport.getPhotoKey(),
                lostReport.getDescription(),
                lostReport.getLostAt(),
                lostReport.getLocation(),
                lostReport.getStatus(),
                lostReport.getVenueId(),
                lostReport.getContactEmail(),
                toItemAttributesDto(lostReport.getAttributes())
        );
    }
}
