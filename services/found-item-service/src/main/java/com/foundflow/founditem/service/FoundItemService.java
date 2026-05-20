package com.foundflow.founditem.service;

import com.foundflow.founditem.domain.FoundItem;
import com.foundflow.common.domain.ItemAttributes;
import com.foundflow.founditem.domain.ItemStatus;
import com.foundflow.founditem.dto.CreateFoundItemRequest;
import com.foundflow.founditem.dto.FoundItemResponse;
import com.foundflow.founditem.dto.HistogramResponse;
import com.foundflow.founditem.dto.ItemAttributesDto;
import com.foundflow.founditem.dto.TimeBucketCount;
import com.foundflow.founditem.dto.UpdateFoundItemRequest;
import com.foundflow.founditem.repository.BucketCountView;
import com.foundflow.founditem.repository.FoundItemRepository;
import com.foundflow.founditem.security.VenueAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
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
public class FoundItemService {

    private final FoundItemRepository foundItemRepository;
    private final VenueAccessService venueAccessService;

    public FoundItemService(
            FoundItemRepository foundItemRepository,
            VenueAccessService venueAccessService
    ) {
        this.foundItemRepository = foundItemRepository;
        this.venueAccessService = venueAccessService;
    }

    public FoundItemResponse createFoundItem(
            CreateFoundItemRequest request,
            Jwt jwt
    ) {
        UUID venueId = venueAccessService.isAdmin(jwt)
                ? request.venueId()
                : venueAccessService.getVenueId(jwt);

        FoundItem foundItem = new FoundItem(
                request.photoKey(),
                request.description(),
                request.foundAt(),
                request.locationHint(),
                ItemStatus.STORED,
                venueId,
                request.reporterId(),
                toItemAttributes(request.attributes())
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

                    foundItem.setPhotoKey(request.photoKey());
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
                    foundItem.setReporterId(request.reporterId());
                    foundItem.setAttributes(toItemAttributes(request.attributes()));

                    FoundItem updatedFoundItem = foundItemRepository.save(foundItem);
                    return toResponse(updatedFoundItem);
                });
    }

    public boolean deleteFoundItem(UUID id, Jwt jwt) {
        return foundItemRepository.findById(id)
                .map(foundItem -> {
                    verifyVenueAccess(jwt, foundItem.getVenueId());
                    foundItemRepository.delete(foundItem);
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
}
