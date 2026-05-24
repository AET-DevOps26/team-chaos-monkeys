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
import com.foundflow.photo.storage.PhotoData;
import com.foundflow.photo.storage.PhotoNotFoundException;
import com.foundflow.photo.storage.PhotoStorage;
import com.foundflow.photo.storage.PhotoStorageException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
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

    private static final long MAX_PHOTO_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final List<String> ALLOWED_PHOTO_CONTENT_TYPES = List.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final LostReportRepository lostReportRepository;
    private final VenueAccessService venueAccessService;
    private final PhotoStorage photoStorage;

    public LostReportService(
            LostReportRepository lostReportRepository,
            VenueAccessService venueAccessService,
            PhotoStorage photoStorage
    ) {
        this.lostReportRepository = lostReportRepository;
        this.venueAccessService = venueAccessService;
        this.photoStorage = photoStorage;
    }

    public LostReportResponse createLostReport(
            CreateLostReportRequest request,
            Jwt jwt
    ) {
        return createLostReport(request, null, jwt);
    }

    public LostReportResponse createLostReport(
            CreateLostReportRequest request,
            MultipartFile photo,
            Jwt jwt
    ) {
        UUID venueId = resolveCreateVenueId(request, jwt);
        String photoKey = hasPhoto(photo) ? storePhoto(photo) : null;

        LostReport lostReport = new LostReport(
                photoKey,
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

    public Optional<LostReportResponse> updateLostReportPhoto(
            UUID id,
            MultipartFile photo,
            Jwt jwt
    ) {
        return lostReportRepository.findById(id)
                .map(lostReport -> {
                    verifyVenueAccess(jwt, lostReport.getVenueId());

                    String previousPhotoKey = lostReport.getPhotoKey();
                    String photoKey = storePhoto(photo);
                    lostReport.setPhotoKey(photoKey);

                    LostReport updatedReport = lostReportRepository.save(lostReport);
                    if (previousPhotoKey != null && !previousPhotoKey.isBlank()) {
                        photoStorage.delete(previousPhotoKey);
                    }

                    return toResponse(updatedReport);
                });
    }

    public Optional<PhotoData> getLostReportPhoto(UUID id, Jwt jwt) {
        return lostReportRepository.findById(id)
                .map(lostReport -> {
                    verifyVenueAccess(jwt, lostReport.getVenueId());
                    String photoKey = lostReport.getPhotoKey();
                    if (photoKey == null || photoKey.isBlank()) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lost report has no photo.");
                    }
                    try {
                        return photoStorage.retrieve(photoKey);
                    } catch (PhotoNotFoundException exception) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found.", exception);
                    }
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

    private String storePhoto(MultipartFile photo) {
        validatePhoto(photo);
        try {
            return photoStorage.store(new PhotoData(
                    photo.getInputStream(),
                    photo.getContentType(),
                    photo.getSize()
            ));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read uploaded photo.", exception);
        } catch (PhotoStorageException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not store uploaded photo.", exception);
        }
    }

    private boolean hasPhoto(MultipartFile photo) {
        return photo != null && !photo.isEmpty();
    }

    private void validatePhoto(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo must not be empty.");
        }
        if (photo.getSize() > MAX_PHOTO_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Photo must be at most 10 MB.");
        }
        if (!ALLOWED_PHOTO_CONTENT_TYPES.contains(photo.getContentType())) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported photo content type.");
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
