package com.foundflow.founditem.service;

import com.foundflow.founditem.domain.FoundItem;
import com.foundflow.common.domain.ItemAttributes;
import com.foundflow.founditem.domain.ItemStatus;
import com.foundflow.founditem.dto.CreateFoundItemRequest;
import com.foundflow.founditem.dto.FoundItemResponse;
import com.foundflow.founditem.dto.ItemAttributesDto;
import com.foundflow.founditem.dto.UpdateFoundItemRequest;
import com.foundflow.founditem.repository.FoundItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FoundItemService {

    private final FoundItemRepository foundItemRepository;

    public FoundItemService(FoundItemRepository foundItemRepository) {
        this.foundItemRepository = foundItemRepository;
    }

    public FoundItemResponse createFoundItem(CreateFoundItemRequest request) {
        FoundItem foundItem = new FoundItem(
                request.photoKey(),
                request.description(),
                request.foundAt(),
                request.locationHint(),
                ItemStatus.STORED,
                request.venueId(),
                request.reporterId(),
                toItemAttributes(request.attributes())
        );

        FoundItem savedFoundItem = foundItemRepository.save(foundItem);
        return toResponse(savedFoundItem);
    }

    public List<FoundItemResponse> getAllFoundItems() {
        return foundItemRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public Optional<FoundItemResponse> getFoundItemById(UUID id) {
        return foundItemRepository.findById(id)
                .map(this::toResponse);
    }

    public Optional<FoundItemResponse> updateFoundItem(
            UUID id,
            UpdateFoundItemRequest request
    ) {
        return foundItemRepository.findById(id)
                .map(foundItem -> {
                    foundItem.setPhotoKey(request.photoKey());
                    foundItem.setDescription(request.description());
                    foundItem.setFoundAt(request.foundAt());
                    foundItem.setLocationHint(request.locationHint());
                    foundItem.setStatus(request.status());
                    foundItem.setVenueId(request.venueId());
                    foundItem.setReporterId(request.reporterId());
                    foundItem.setAttributes(toItemAttributes(request.attributes()));

                    FoundItem updatedFoundItem = foundItemRepository.save(foundItem);
                    return toResponse(updatedFoundItem);
                });
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