package com.foundflow.founditem.service;

import com.foundflow.common.domain.ItemAttributes;
import com.foundflow.founditem.domain.FoundItem;
import com.foundflow.founditem.domain.ItemStatus;
import com.foundflow.founditem.dto.CreateFoundItemRequest;
import com.foundflow.founditem.dto.FoundItemResponse;
import com.foundflow.founditem.dto.ItemAttributesDto;
import com.foundflow.founditem.dto.UpdateFoundItemRequest;
import com.foundflow.founditem.messaging.FoundItemEventPublisher;
import com.foundflow.founditem.repository.BucketCountView;
import com.foundflow.founditem.repository.FoundItemRepository;
import com.foundflow.founditem.security.VenueAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

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

    @Mock
    private FoundItemEventPublisher eventPublisher;

    private final VenueAccessService venueAccessService = new VenueAccessService();

    @Test
    void createFoundItem_shouldUseVenueFromJwtForStaff() {
        FoundItemService service = foundItemService();

        UUID jwtVenueId = UUID.randomUUID();
        UUID requestVenueId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        LocalDateTime foundAt = LocalDateTime.of(2026, 5, 12, 14, 30);

        CreateFoundItemRequest request = new CreateFoundItemRequest(
                "photo-123",
                "Schwarzer Rucksack",
                foundAt,
                "Neben Buehne 2",
                requestVenueId,
                reporterId,
                new ItemAttributesDto("Bag", "Nike", "Black", List.of("Roter Anhaenger"))
        );

        when(foundItemRepository.save(any(FoundItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FoundItemResponse response = service.createFoundItem(request, staffJwt(jwtVenueId));

        ArgumentCaptor<FoundItem> captor = ArgumentCaptor.forClass(FoundItem.class);
        verify(foundItemRepository).save(captor.capture());

        assertEquals(jwtVenueId, captor.getValue().getVenueId());
        assertEquals(ItemStatus.STORED, captor.getValue().getStatus());
        assertEquals(jwtVenueId, response.venueId());
        verify(eventPublisher).publishFoundItemLogged(captor.getValue());
    }

    @Test
    void getFoundItemById_shouldReturnResponseForOwnVenue() {
        FoundItemService service = foundItemService();

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        FoundItem foundItem = foundItem(venueId);

        when(foundItemRepository.findById(id)).thenReturn(Optional.of(foundItem));

        Optional<FoundItemResponse> response = service.getFoundItemById(id, staffJwt(venueId));

        assertTrue(response.isPresent());
        assertEquals("Schwarzer Rucksack", response.get().description());
        assertEquals(venueId, response.get().venueId());
        verify(foundItemRepository).findById(id);
    }

    @Test
    void getAllFoundItems_shouldUseVenueRepositoryForStaff() {
        FoundItemService service = foundItemService();

        UUID venueId = UUID.randomUUID();
        when(foundItemRepository.findByVenueIdAndStatus(venueId, ItemStatus.STORED))
                .thenReturn(List.of(foundItem(venueId)));

        List<FoundItemResponse> responses =
                service.getAllFoundItems(ItemStatus.STORED, staffJwt(venueId));

        assertEquals(1, responses.size());
        assertEquals(venueId, responses.get(0).venueId());
        verify(foundItemRepository).findByVenueIdAndStatus(venueId, ItemStatus.STORED);
        verify(foundItemRepository, never()).findAll();
    }

    @Test
    void histogram_shouldBucketAccessibleFoundItemsByDayWeekAndMonth() {
        FoundItemService service = foundItemService();

        UUID venueId = UUID.randomUUID();
        when(foundItemRepository.findDailyBuckets(venueId, null)).thenReturn(List.of(
                bucket(java.time.LocalDate.of(2026, 5, 19), 2),
                bucket(java.time.LocalDate.of(2026, 5, 20), 1)
        ));

        var histogram = service.getFoundItemHistogram(null, staffJwt(venueId));

        assertEquals(2, histogram.perDay().size());
        assertEquals(java.time.LocalDate.of(2026, 5, 19), histogram.perDay().get(0).bucketStart());
        assertEquals(2, histogram.perDay().get(0).count());
        assertEquals(1, histogram.perWeek().size());
        assertEquals(java.time.LocalDate.of(2026, 5, 18), histogram.perWeek().get(0).bucketStart());
        assertEquals(3, histogram.perMonth().get(0).count());
    }

    @Test
    void updateFoundItem_shouldKeepVenueForStaff() {
        FoundItemService service = foundItemService();

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        FoundItem existingItem = foundItem(venueId);

        UpdateFoundItemRequest request = new UpdateFoundItemRequest(
                "new-photo",
                "Neue Beschreibung",
                LocalDateTime.of(2026, 5, 12, 18, 45),
                "Neuer Ort",
                ItemStatus.RESERVED,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new ItemAttributesDto("Bag", "Adidas", "Blue", List.of("Neues Merkmal"))
        );

        when(foundItemRepository.findById(id)).thenReturn(Optional.of(existingItem));
        when(foundItemRepository.save(any(FoundItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<FoundItemResponse> response =
                service.updateFoundItem(id, request, staffJwt(venueId));

        assertTrue(response.isPresent());
        assertEquals(ItemStatus.RESERVED, response.get().status());
        assertEquals(venueId, response.get().venueId());
        verify(foundItemRepository).save(existingItem);
        ArgumentCaptor<FoundItem> publishedItem = ArgumentCaptor.forClass(FoundItem.class);
        verify(eventPublisher).publishFoundItemUpdated(publishedItem.capture());
        assertSame(existingItem, publishedItem.getValue());
        assertEquals("Neue Beschreibung", publishedItem.getValue().getDescription());
        assertEquals(ItemStatus.RESERVED, publishedItem.getValue().getStatus());
        verify(eventPublisher, never()).publishFoundItemLogged(any());
    }

    private FoundItemService foundItemService() {
        return new FoundItemService(
                foundItemRepository,
                venueAccessService,
                eventPublisher
        );
    }

    private FoundItem foundItem(UUID venueId) {
        return foundItem(venueId, LocalDateTime.of(2026, 5, 12, 14, 30));
    }

    private FoundItem foundItem(UUID venueId, LocalDateTime foundAt) {
        return new FoundItem(
                "photo-123",
                "Schwarzer Rucksack",
                foundAt,
                "Neben Buehne 2",
                ItemStatus.STORED,
                venueId,
                UUID.randomUUID(),
                new ItemAttributes("Bag", "Nike", "Black", List.of("Roter Anhaenger"))
        );
    }

    private Jwt staffJwt(UUID venueId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("roles", List.of("STAFF"))
                .claim("venue_id", venueId.toString())
                .build();
    }

    private BucketCountView bucket(java.time.LocalDate bucketStart, long count) {
        return new BucketCountView() {
            @Override
            public java.time.LocalDate getBucketStart() {
                return bucketStart;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }
}
