package com.foundflow.founditem.service;

import com.foundflow.founditem.domain.FoundItem;
import com.foundflow.common.domain.ItemAttributes;
import com.foundflow.founditem.domain.ItemStatus;
import com.foundflow.founditem.dto.CreateFoundItemRequest;
import com.foundflow.founditem.dto.FoundItemResponse;
import com.foundflow.founditem.dto.HistogramResponse;
import com.foundflow.founditem.dto.ItemAttributesDto;
import com.foundflow.founditem.dto.PublicFoundItemResponse;
import com.foundflow.founditem.dto.TimeBucketCount;
import com.foundflow.founditem.dto.UpdateFoundItemRequest;
import com.foundflow.founditem.messaging.FoundItemEventPublisher;
import com.foundflow.founditem.repository.BucketCountView;
import com.foundflow.founditem.repository.FoundItemRepository;
import com.foundflow.founditem.security.VenueAccessService;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
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
public class FoundItemService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoundItemService.class);
    private final FoundItemRepository foundItemRepository;
    private final VenueAccessService venueAccessService;
    private final PhotoStorage photoStorage;
    private final Duration photoUrlTtl;
    private final FoundItemEventPublisher eventPublisher;
    private final AttributeExtractionService attributeExtractionService;

    public FoundItemService(
            FoundItemRepository foundItemRepository,
            VenueAccessService venueAccessService,
            PhotoStorage photoStorage,
            @Value("${photo-storage.signed-url-ttl:PT10M}") Duration photoUrlTtl,
            FoundItemEventPublisher eventPublisher,
            AttributeExtractionService attributeExtractionService
    ) {
        this.foundItemRepository = foundItemRepository;
        this.venueAccessService = venueAccessService;
        this.photoStorage = photoStorage;
        this.photoUrlTtl = photoUrlTtl;
        this.eventPublisher = eventPublisher;
        this.attributeExtractionService = attributeExtractionService;
    }

    public FoundItemResponse createFoundItem(
            CreateFoundItemRequest request,
            Jwt jwt
    ) {
        UUID venueId = venueAccessService.isAdmin(jwt)
                ? request.venueId()
                : venueAccessService.getVenueId(jwt);
        UUID reporterId = request.reporterId() != null
                ? request.reporterId()
                : venueAccessService.getUserId(jwt);

        ItemAttributes attributes = toItemAttributes(request.attributes());

        FoundItem foundItem = new FoundItem(
                null,
                request.description(),
                request.foundAt(),
                request.locationHint(),
                ItemStatus.STORED,
                venueId,
                reporterId,
                attributes
        );

        FoundItem savedFoundItem = foundItemRepository.save(foundItem);

        return toResponse(savedFoundItem);
    }

    public List<FoundItemResponse> getAllFoundItems(
            ItemStatus status,
            Jwt jwt
    ) {
        return findAccessibleFoundItems(status, jwt)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public Optional<FoundItemResponse> getFoundItemById(
            UUID id,
            Jwt jwt
    ) {
        return foundItemRepository.findById(id)
                .map(foundItem -> {
                    verifyVenueAccess(jwt, foundItem.getVenueId());
                    return foundItem;
                })
                .map(this::toResponse);
    }

    public Optional<FoundItemResponse> updateFoundItem(
            UUID id,
            UpdateFoundItemRequest request,
            Jwt jwt
    ) {
        return foundItemRepository.findById(id)
                .map(foundItem -> {
                    verifyVenueAccess(jwt, foundItem.getVenueId());

                    foundItem.setDescription(request.description());
                    foundItem.setFoundAt(request.foundAt());
                    foundItem.setLocationHint(request.locationHint());
                    foundItem.setStatus(request.status());
                    if (venueAccessService.isAdmin(jwt)) {
                        if (request.venueId() == null) {
                            throw new ResponseStatusException(
                                    HttpStatus.BAD_REQUEST,
                                    "venueId is required when updating a found item."
                            );
                        }
                        foundItem.setVenueId(request.venueId());
                    }
                    if (request.reporterId() != null) {
                        foundItem.setReporterId(request.reporterId());
                    }
                    foundItem.setAttributes(toItemAttributes(request.attributes()));

                    FoundItem updatedFoundItem = foundItemRepository.save(foundItem);
                    if (hasStoredPhoto(updatedFoundItem.getPhotoKey())) {
                        eventPublisher.publishFoundItemUpdated(updatedFoundItem);
                    }
                    return toResponse(updatedFoundItem);
                });
    }

    public Optional<FoundItemResponse> updateFoundItemPhoto(
            UUID id,
            MultipartFile photo,
            Jwt jwt
    ) {
        return foundItemRepository.findById(id)
                .map(foundItem -> {
                    verifyVenueAccess(jwt, foundItem.getVenueId());

                    String previousPhotoKey = foundItem.getPhotoKey();
                    boolean hadPhoto = hasStoredPhoto(previousPhotoKey);
                    String photoKey = storePhoto(photo);
                    foundItem.setPhotoKey(photoKey);

                    FoundItem updatedFoundItem = saveOrCompensate(foundItem, photoKey, id);
                    enrichFromPhotoIfMissingAttributes(updatedFoundItem, photoKey);
                    if (hadPhoto) {
                        eventPublisher.publishFoundItemUpdated(updatedFoundItem);
                    } else {
                        eventPublisher.publishFoundItemCreated(updatedFoundItem);
                    }
                    safeDeletePhoto(previousPhotoKey, id);

                    return toResponse(updatedFoundItem);
                });
    }

    public Optional<PhotoData> getFoundItemPhoto(UUID id, Jwt jwt) {
        return foundItemRepository.findById(id)
                .map(foundItem -> {
                    verifyVenueAccess(jwt, foundItem.getVenueId());
                    String photoKey = foundItem.getPhotoKey();
                    if (photoKey == null || photoKey.isBlank()) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Found item has no photo.");
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

    public Optional<PhotoUrlResponse> getFoundItemPhotoUrl(UUID id, Jwt jwt) {
        return foundItemRepository.findById(id)
                .map(foundItem -> {
                    verifyVenueAccess(jwt, foundItem.getVenueId());
                    return new PhotoUrlResponse(signedUrlFor(foundItem.getPhotoKey()));
                });
    }

    public Optional<PublicFoundItemResponse> getPublicFoundItemDetail(UUID id, UUID venueId) {
        return foundItemRepository.findById(id)
                .filter(foundItem -> foundItem.getVenueId() != null && foundItem.getVenueId().equals(venueId))
                .map(foundItem -> toPublicResponse(foundItem, signedUrlFor(foundItem.getPhotoKey())));
    }

    public boolean deleteFoundItem(UUID id, Jwt jwt) {
        return foundItemRepository.findById(id)
                .map(foundItem -> {
                    verifyVenueAccess(jwt, foundItem.getVenueId());
                    String photoKey = foundItem.getPhotoKey();
                    foundItemRepository.delete(foundItem);
                    safeDeletePhoto(photoKey, id);
                    return true;
                })
                .orElse(false);
    }

    public long countFoundItems(ItemStatus status, Jwt jwt) {
        return countFoundItems(status, null, jwt);
    }

    public long countFoundItems(ItemStatus status, UUID requestedVenueId, Jwt jwt) {
        UUID venueId = resolveVenueFilter(requestedVenueId, jwt);
        if (venueId != null) {
            if (status == null) {
                return foundItemRepository.countByVenueId(venueId);
            }

            return foundItemRepository.countByVenueIdAndStatus(venueId, status);
        }

        if (venueAccessService.isAdmin(jwt)) {
            if (status == null) {
                return foundItemRepository.count();
            }

            return foundItemRepository.countByStatus(status);
        }
        throw new AccessDeniedException("Missing venue access.");
    }

    public HistogramResponse getFoundItemHistogram(ItemStatus status, Jwt jwt) {
        return getFoundItemHistogram(status, null, jwt);
    }

    public HistogramResponse getFoundItemHistogram(ItemStatus status, UUID requestedVenueId, Jwt jwt) {
        UUID venueId = resolveVenueFilter(requestedVenueId, jwt);
        List<TimeBucketCount> perDay = toTimeBucketCounts(
                foundItemRepository.findDailyBuckets(
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

    private List<FoundItem> findAccessibleFoundItems(ItemStatus status, Jwt jwt) {
        if (venueAccessService.isAdmin(jwt)) {
            if (status == null) {
                return foundItemRepository.findAll();
            }

            return foundItemRepository.findByStatus(status);
        }

        UUID venueId = venueAccessService.getVenueId(jwt);
        if (status == null) {
            return foundItemRepository.findByVenueId(venueId);
        }

        return foundItemRepository.findByVenueIdAndStatus(venueId, status);
    }

    private void verifyVenueAccess(Jwt jwt, UUID resourceVenueId) {
        if (!venueAccessService.canAccessVenue(jwt, resourceVenueId)) {
            throw new AccessDeniedException("No access to this venue.");
        }
    }

    private boolean hasStoredPhoto(String photoKey) {
        return photoKey != null && !photoKey.isBlank();
    }

    private void enrichFromPhotoIfMissingAttributes(FoundItem foundItem, String photoKey) {
        if (hasMeaningfulAttributes(foundItem.getAttributes())) {
            return;
        }

        attributeExtractionService.extractWithLocation(foundItem.getDescription(), photoKey)
                .ifPresent(extracted -> {
                    foundItem.setAttributes(extracted.attributes());
                    if (extracted.location() != null) {
                        foundItem.setLocationHint(extracted.location());
                    }
                    foundItemRepository.save(foundItem);
                });
    }

    private boolean hasMeaningfulAttributes(ItemAttributes attributes) {
        if (attributes == null) {
            return false;
        }

        return hasText(attributes.getCategory())
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

    private FoundItem saveOrCompensate(FoundItem foundItem, String newlyStoredPhotoKey, UUID itemId) {
        try {
            return foundItemRepository.save(foundItem);
        } catch (RuntimeException exception) {
            if (newlyStoredPhotoKey != null) {
                safeDeletePhoto(newlyStoredPhotoKey, itemId);
            }
            throw exception;
        }
    }

    private void safeDeletePhoto(String photoKey, UUID itemId) {
        if (photoKey == null || photoKey.isBlank()) {
            return;
        }
        try {
            photoStorage.delete(photoKey);
        } catch (PhotoStorageException exception) {
            LOGGER.warn("Could not delete photo {} for found item {}.", photoKey, itemId, exception);
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

    private FoundItemResponse toResponse(FoundItem foundItem) {
        return new FoundItemResponse(
                foundItem.getId(),
                foundItem.getPhotoKey(),
                foundItem.getDescription(),
                foundItem.getFoundAt(),
                foundItem.getLocationHint(),
                foundItem.getStatus(),
                foundItem.getVenueId(),
                foundItem.getReporterId(),
                toItemAttributesDto(foundItem.getAttributes())
        );
    }

    private URI signedUrlFor(String photoKey) {
        if (photoKey == null || photoKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Found item has no photo.");
        }
        try {
            return photoStorage.signedUrl(photoKey, photoUrlTtl);
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
    }

    private PublicFoundItemResponse toPublicResponse(FoundItem foundItem, URI photoUrl) {
        return new PublicFoundItemResponse(
                foundItem.getId(),
                foundItem.getDescription(),
                foundItem.getFoundAt(),
                foundItem.getLocationHint(),
                foundItem.getStatus(),
                toItemAttributesDto(foundItem.getAttributes()),
                photoUrl
        );
    }
}
