package com.foundflow.founditem.service;

import com.foundflow.founditem.domain.FoundItem;
import com.foundflow.common.domain.ItemAttributes;
import com.foundflow.founditem.domain.ItemStatus;
import com.foundflow.founditem.dto.CreateFoundItemRequest;
import com.foundflow.founditem.dto.FoundItemResponse;
import com.foundflow.founditem.dto.ItemAttributesDto;
import com.foundflow.founditem.dto.UpdateFoundItemRequest;
import com.foundflow.founditem.repository.FoundItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FoundItemServiceTest {

    @Mock
    private FoundItemRepository foundItemRepository;

    @Test
    void createFoundItem_shouldSaveItemWithStoredStatus() {
        FoundItemService foundItemService = new FoundItemService(foundItemRepository);

        UUID venueId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        LocalDateTime foundAt = LocalDateTime.of(2026, 5, 12, 14, 30);

        CreateFoundItemRequest request = new CreateFoundItemRequest(
                "photo-123",
                "Schwarzer Rucksack",
                foundAt,
                "Neben Bühne 2",
                venueId,
                reporterId,
                new ItemAttributesDto(
                        "Bag",
                        "Nike",
                        "Black",
                        List.of("Roter Anhänger", "Kratzer vorne")
                )
        );

        when(foundItemRepository.save(any(FoundItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FoundItemResponse response = foundItemService.createFoundItem(request);

        ArgumentCaptor<FoundItem> captor = ArgumentCaptor.forClass(FoundItem.class);
        verify(foundItemRepository).save(captor.capture());

        FoundItem savedItem = captor.getValue();

        assertEquals("photo-123", savedItem.getPhotoKey());
        assertEquals("Schwarzer Rucksack", savedItem.getDescription());
        assertEquals(foundAt, savedItem.getFoundAt());
        assertEquals("Neben Bühne 2", savedItem.getLocationHint());
        assertEquals(ItemStatus.STORED, savedItem.getStatus());
        assertEquals(venueId, savedItem.getVenueId());
        assertEquals(reporterId, savedItem.getReporterId());

        assertNotNull(savedItem.getAttributes());
        assertEquals("Bag", savedItem.getAttributes().getCategory());
        assertEquals("Nike", savedItem.getAttributes().getBrand());
        assertEquals("Black", savedItem.getAttributes().getColor());
        assertEquals(List.of("Roter Anhänger", "Kratzer vorne"), savedItem.getAttributes().getMarks());

        assertEquals(ItemStatus.STORED, response.status());
        assertEquals("Schwarzer Rucksack", response.description());
    }

    @Test
    void getFoundItemById_shouldReturnResponseWhenItemExists() {
        FoundItemService foundItemService = new FoundItemService(foundItemRepository);

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();

        FoundItem foundItem = new FoundItem(
                "photo-123",
                "Schwarzer Rucksack",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                "Neben Bühne 2",
                ItemStatus.STORED,
                venueId,
                reporterId,
                new ItemAttributes(
                        "Bag",
                        "Nike",
                        "Black",
                        List.of("Roter Anhänger")
                )
        );

        when(foundItemRepository.findById(id)).thenReturn(Optional.of(foundItem));

        Optional<FoundItemResponse> response = foundItemService.getFoundItemById(id);

        assertTrue(response.isPresent());
        assertEquals("Schwarzer Rucksack", response.get().description());
        assertEquals(ItemStatus.STORED, response.get().status());
        assertEquals("Nike", response.get().attributes().brand());

        verify(foundItemRepository).findById(id);
    }

    @Test
    void getFoundItemById_shouldReturnEmptyWhenItemDoesNotExist() {
        FoundItemService foundItemService = new FoundItemService(foundItemRepository);

        UUID id = UUID.randomUUID();

        when(foundItemRepository.findById(id)).thenReturn(Optional.empty());

        Optional<FoundItemResponse> response = foundItemService.getFoundItemById(id);

        assertTrue(response.isEmpty());
        verify(foundItemRepository).findById(id);
    }

    @Test
    void updateFoundItem_shouldUpdateExistingItem() {
        FoundItemService foundItemService = new FoundItemService(foundItemRepository);

        UUID id = UUID.randomUUID();

        FoundItem existingItem = new FoundItem(
                "old-photo",
                "Alte Beschreibung",
                LocalDateTime.of(2026, 5, 10, 10, 0),
                "Alter Ort",
                ItemStatus.STORED,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new ItemAttributes(
                        "Old Category",
                        "Old Brand",
                        "Old Color",
                        List.of("Altes Merkmal")
                )
        );

        UUID newVenueId = UUID.randomUUID();
        UUID newReporterId = UUID.randomUUID();

        UpdateFoundItemRequest request = new UpdateFoundItemRequest(
                "new-photo",
                "Neue Beschreibung",
                LocalDateTime.of(2026, 5, 12, 18, 45),
                "Neuer Ort",
                ItemStatus.RESERVED,
                newVenueId,
                newReporterId,
                new ItemAttributesDto(
                        "Bag",
                        "Adidas",
                        "Blue",
                        List.of("Neues Merkmal")
                )
        );

        when(foundItemRepository.findById(id)).thenReturn(Optional.of(existingItem));
        when(foundItemRepository.save(any(FoundItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<FoundItemResponse> response = foundItemService.updateFoundItem(id, request);

        assertTrue(response.isPresent());
        assertEquals("Neue Beschreibung", response.get().description());
        assertEquals(ItemStatus.RESERVED, response.get().status());
        assertEquals("Adidas", response.get().attributes().brand());
        assertEquals(newVenueId, response.get().venueId());
        assertEquals(newReporterId, response.get().reporterId());

        verify(foundItemRepository).findById(id);
        verify(foundItemRepository).save(existingItem);
    }
}