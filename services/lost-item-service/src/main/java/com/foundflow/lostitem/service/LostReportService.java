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
import com.foundflow.lostitem.messaging.LostReportEventPublisher;
import com.foundflow.lostitem.repository.BucketCountView;
import com.foundflow.lostitem.repository.LostReportRepository;
import com.foundflow.lostitem.security.VenueAccessService;
import com.foundflow.genai.client.AttributeExtractionService;
import com.foundflow.photo.storage.PhotoConstraints;
import com.foundflow.photo.storage.PhotoData;
import com.foundflow.photo.storage.PhotoNotFoundException;
import com.foundflow.photo.storage.PhotoStorage;
import com.foundflow.photo.storage.PhotoStorageException;
import com.foundflow.photo.storage.PhotoUrlResponse;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.time.DayOfWeek;
import java.time.Duration;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(LostReportService.class);
    private final LostReportRepository lostReportRepository;
    private final VenueAccessService venueAccessService;
    private final PhotoStorage photoStorage;
    private final Duration photoUrlTtl;
    private final LostReportEventPublisher eventPublisher;
    private final AttributeExtractionService attributeExtractionService;

    public LostReportService(
            LostReportRepository lostReportRepository,
            VenueAccessService venueAccessService,
            PhotoStorage photoStorage,
            @Value("${photo-storage.signed-url-ttl:PT10M}") Duration photoUrlTtl,
            LostReportEventPublisher eventPublisher,
            AttributeExtractionService attributeExtractionService
    ) {
        this.lostReportRepository = lostReportRepository;
        this.venueAccessService = venueAccessService;
        this.photoStorage = photoStorage;
        this.photoUrlTtl = photoUrlTtl;
        this.eventPublisher = eventPublisher;
        this.attributeExtractionService = attributeExtractionService;
    }

    @Transactional
    public LostReportResponse createLostReport(
            CreateLostReportRequest request,
            Jwt jwt
    ) {
        return createLostReport(request, null, jwt);
    }

    @Transactional
    public LostReportResponse createLostReport(
            CreateLostReportRequest request,
            MultipartFile photo,
            Jwt jwt
    ) {
        UUID venueId = resolveCreateVenueId(request, jwt);
        String photoKey = photo == null || photo.isEmpty() ? null : storePhoto(photo);

        ItemAttributes attributes = toItemAttributes(request.attributes());

        LostReport lostReport = new LostReport(
                photoKey,
                request.description(),
                request.lostAt(),
                request.location(),
                ReportStatus.OPEN,
                venueId,
                request.contactEmail(),
                attributes
        );

        LostReport savedLostReport = saveOrCompensate(lostReport, photoKey, null);
        enrichIfMissingAttributes(savedLostReport, photoKey);

        LOGGER.info("Lost report created lostReport={} venue={}", savedLostReport.getId(), venueId);
        eventPublisher.publishLostReportCreated(savedLostReport);
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

    @Transactional
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
                    LOGGER.info(
                            "Lost report updated lostReport={} venue={} status={}",
                            updatedReport.getId(),
                            updatedReport.getVenueId(),
                            updatedReport.getStatus()
                    );
                    eventPublisher.publishLostReportUpdated(updatedReport);
                    return toResponse(updatedReport);
                });
    }

    @Transactional
    public Optional<LostReportResponse> updateLostReportPhoto(
            UUID id,
            MultipartFile photo,
            Jwt jwt
    ) {
        return lostReportRepository.findById(id)
                .map(lostReport -> {
                    verifyPhotoUpdateAccess(jwt, lostReport);

                    String previousPhotoKey = lostReport.getPhotoKey();
                    String photoKey = storePhoto(photo);
                    lostReport.setPhotoKey(photoKey);

                    LostReport updatedReport = saveOrCompensate(lostReport, photoKey, id);
                    enrichIfMissingAttributes(updatedReport, photoKey);
                    LOGGER.info(
                            "Lost report photo updated lostReport={} venue={}",
                            updatedReport.getId(),
                            updatedReport.getVenueId()
                    );
                    eventPublisher.publishLostReportUpdated(updatedReport);
                    safeDeletePhoto(previousPhotoKey, id);

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
                    } catch (PhotoStorageException exception) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY,
                                "Photo storage backend failure.",
                                exception
                        );
                    }
                });
    }

    public Optional<PhotoUrlResponse> getLostReportPhotoUrl(UUID id, Jwt jwt) {
        return lostReportRepository.findById(id)
                .map(lostReport -> {
                    verifyVenueAccess(jwt, lostReport.getVenueId());
                    String photoKey = lostReport.getPhotoKey();
                    if (photoKey == null || photoKey.isBlank()) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lost report has no photo.");
                    }
                    try {
                        URI signedUrl = photoStorage.signedUrl(photoKey, photoUrlTtl);
                        return new PhotoUrlResponse(signedUrl);
                    } catch (PhotoNotFoundException exception) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found.", exception);
                    } catch (UnsupportedOperationException exception) {
                        throw new ResponseStatusException(
                                HttpStatus.NOT_IMPLEMENTED,
                                "Signed photo URLs are not supported by this storage backend.",
                                exception
                        );
                    } catch (PhotoStorageException exception) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY,
                                "Photo storage backend failure.",
                                exception
                        );
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

    private void verifyPhotoUpdateAccess(Jwt jwt, LostReport lostReport) {
        if (jwt != null) {
            verifyVenueAccess(jwt, lostReport.getVenueId());
            return;
        }

        if (lostReport.getPhotoKey() != null && !lostReport.getPhotoKey().isBlank()) {
            throw new AccessDeniedException("Photo already exists for this lost report.");
        }
    }

    /**
     * Best-effort GenAI enrichment when the guest supplied no structured
     * attributes. Runs for text-only reports too: the extraction service
     * builds a description-only request when {@code photoKey} is null, so a
     * photo-less report like "purple shirt" still gets a category and a
     * generated description for the matching embedding. Extraction failures
     * are swallowed so intake never blocks.
     */
    private void enrichIfMissingAttributes(LostReport lostReport, String photoKey) {
        if (hasMeaningfulAttributes(lostReport.getAttributes())) {
            return;
        }

        attributeExtractionService.extractWithLocation(lostReport.getDescription(), photoKey)
                .ifPresent(extracted -> {
                    lostReport.setAttributes(extracted.attributes());
                    // Only fill in a location the guest didn't type — never clobber theirs.
                    if (extracted.location() != null && !hasText(lostReport.getLocation())) {
                        lostReport.setLocation(extracted.location());
                    }
                    lostReportRepository.save(lostReport);
                });
    }

    private boolean hasMeaningfulAttributes(ItemAttributes attributes) {
        if (attributes == null) {
            return false;
        }

        return hasText(attributes.getCategory())
                || hasText(attributes.getDescription())
                || hasText(attributes.getBrand())
                || hasText(attributes.getColor())
                || (attributes.getMarks() != null && attributes.getMarks().stream().anyMatch(this::hasText));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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

    private LostReport saveOrCompensate(LostReport lostReport, String newlyStoredPhotoKey, UUID reportId) {
        try {
            return lostReportRepository.save(lostReport);
        } catch (RuntimeException exception) {
            if (newlyStoredPhotoKey != null) {
                safeDeletePhoto(newlyStoredPhotoKey, reportId);
            }
            throw exception;
        }
    }

    private void safeDeletePhoto(String photoKey, UUID reportId) {
        if (photoKey == null || photoKey.isBlank()) {
            return;
        }
        try {
            photoStorage.delete(photoKey);
        } catch (PhotoStorageException exception) {
            LOGGER.warn("Could not delete photo {} for lost report {}.", photoKey, reportId, exception);
        }
    }

    private void validatePhoto(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo must not be empty.");
        }
        PhotoConstraints.Violation violation = PhotoConstraints.check(photo.getContentType(), photo.getSize());
        if (violation == null) {
            return;
        }
        throw new ResponseStatusException(statusFor(violation), violation.message());
    }

    private static HttpStatus statusFor(PhotoConstraints.Violation violation) {
        return switch (violation) {
            case TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
            case UNSUPPORTED_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case EMPTY -> HttpStatus.BAD_REQUEST;
        };
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
                dto.description(),
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
                attributes.getDescription(),
                attributes.getBrand(),
                attributes.getColor(),
                attributes.getMarks()
        );
    }

    private LostReportResponse toResponse(LostReport lostReport) {
        return new LostReportResponse(
                lostReport.getId(),
                lostReport.getPhotoKey(),
                photoUrlFor(lostReport.getId(), lostReport.getPhotoKey()),
                lostReport.getDescription(),
                lostReport.getLostAt(),
                lostReport.getLocation(),
                lostReport.getStatus(),
                lostReport.getVenueId(),
                lostReport.getContactEmail(),
                toItemAttributesDto(lostReport.getAttributes())
        );
    }

    private URI photoUrlFor(UUID id, String photoKey) {
        if (photoKey == null || photoKey.isBlank()) {
            return null;
        }
        return URI.create("/api/lost-items/" + id + "/photo");
    }
}
