package com.foundflow.founditem.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import com.foundflow.genai.client.AttributeExtractionService;
import com.foundflow.photo.storage.PhotoStorage;
import com.foundflow.photo.storage.PhotoStorageException;
import com.foundflow.photo.storage.PhotoUrlResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
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
    private PhotoStorage photoStorage;

    @Mock
    private FoundItemEventPublisher eventPublisher;

    @Mock
    private AttributeExtractionService attributeExtractionService;

    private final VenueAccessService venueAccessService = new VenueAccessService();

    @Test
    void createFoundItemWithPhoto_shouldUseVenueFromJwtForStaff() {
        FoundItemService service = service();

        UUID jwtVenueId = UUID.randomUUID();
        UUID requestVenueId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        LocalDateTime foundAt = LocalDateTime.of(2026, 5, 12, 14, 30);
        MockMultipartFile photo = new MockMultipartFile(
                "photo",
                "bag.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "photo-bytes".getBytes()
        );

        CreateFoundItemRequest request = new CreateFoundItemRequest(
                "Schwarzer Rucksack",
                foundAt,
                requestVenueId,
                reporterId
        );

        when(photoStorage.store(any())).thenReturn("found-items/2026/05/generated.jpg");
        when(attributeExtractionService.extract(request.intakeText(), "found-items/2026/05/generated.jpg"))
                .thenReturn(Optional.empty());
        when(foundItemRepository.save(any(FoundItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FoundItemResponse response = service.createFoundItem(request, photo, staffJwt(jwtVenueId));

        ArgumentCaptor<FoundItem> captor = ArgumentCaptor.forClass(FoundItem.class);
        verify(foundItemRepository).save(captor.capture());

        assertEquals(jwtVenueId, captor.getValue().getVenueId());
        assertEquals(ItemStatus.STORED, captor.getValue().getStatus());
        assertNull(captor.getValue().getLocationHint());
        assertNull(captor.getValue().getAttributes());
        assertEquals(jwtVenueId, response.venueId());
        verify(eventPublisher).publishFoundItemLogged(captor.getValue());
    }

    @Test
    void createFoundItemWithPhoto_shouldPersistGeneratedPhotoKey() {
        FoundItemService service = service();

        UUID jwtVenueId = UUID.randomUUID();
        UUID requestVenueId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        MockMultipartFile photo = new MockMultipartFile(
                "photo",
                "bag.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "photo-bytes".getBytes()
        );
        CreateFoundItemRequest request = new CreateFoundItemRequest(
                "Schwarzer Rucksack",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                requestVenueId,
                reporterId
        );

        when(photoStorage.store(any())).thenReturn("found-items/2026/05/generated.jpg");
        when(attributeExtractionService.extract(request.intakeText(), "found-items/2026/05/generated.jpg"))
                .thenReturn(Optional.empty());
        when(foundItemRepository.save(any(FoundItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FoundItemResponse response = service.createFoundItem(request, photo, staffJwt(jwtVenueId));

        ArgumentCaptor<FoundItem> captor = ArgumentCaptor.forClass(FoundItem.class);
        verify(foundItemRepository).save(captor.capture());

        assertEquals("found-items/2026/05/generated.jpg", captor.getValue().getPhotoKey());
        assertEquals("found-items/2026/05/generated.jpg", response.photoKey());
    }

    @Test
    void createFoundItemWithPhoto_shouldPopulateAttributesFromGenAiWhenAvailable() {
        FoundItemService service = service();

        UUID jwtVenueId = UUID.randomUUID();
        UUID requestVenueId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        MockMultipartFile photo = new MockMultipartFile(
                "photo",
                "bag.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "photo-bytes".getBytes()
        );
        CreateFoundItemRequest request = new CreateFoundItemRequest(
                "Schwarzer Rucksack",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                requestVenueId,
                reporterId
        );
        ItemAttributes extracted = new ItemAttributes("Bag", "Nike", "Black", List.of("Roter Anhaenger"));

        when(photoStorage.store(any())).thenReturn("found-items/2026/05/generated.jpg");
        when(attributeExtractionService.extract(request.intakeText(), "found-items/2026/05/generated.jpg"))
                .thenReturn(Optional.of(extracted));
        when(foundItemRepository.save(any(FoundItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FoundItemResponse response = service.createFoundItem(request, photo, staffJwt(jwtVenueId));

        verify(foundItemRepository, times(2)).save(any(FoundItem.class));
        assertEquals("Bag", response.attributes().category());
        assertEquals("Nike", response.attributes().brand());
        assertEquals("Black", response.attributes().color());
        assertEquals(List.of("Roter Anhaenger"), response.attributes().marks());
        verify(eventPublisher).publishFoundItemLogged(argThat(item -> item.getAttributes() == extracted));
    }

    @Test
    void getFoundItemById_shouldReturnResponseForOwnVenue() {
        FoundItemService service = service();

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
        FoundItemService service = service();

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
        FoundItemService service = service();

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
        FoundItemService service = service();

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        FoundItem existingItem = foundItem(venueId);

        UpdateFoundItemRequest request = new UpdateFoundItemRequest(
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
        assertEquals("photo-123", response.get().photoKey());
        verify(foundItemRepository).save(existingItem);
        ArgumentCaptor<FoundItem> publishedItem = ArgumentCaptor.forClass(FoundItem.class);
        verify(eventPublisher).publishFoundItemUpdated(publishedItem.capture());
        assertSame(existingItem, publishedItem.getValue());
        assertEquals("Neue Beschreibung", publishedItem.getValue().getDescription());
        assertEquals(ItemStatus.RESERVED, publishedItem.getValue().getStatus());
        verify(eventPublisher, never()).publishFoundItemLogged(any());
    }

    @Test
    void updateFoundItemPhoto_shouldStorePhotoAndSaveGeneratedKey() {
        FoundItemService service = service();

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        FoundItem existingItem = foundItem(venueId);
        MockMultipartFile photo = new MockMultipartFile(
                "photo",
                "bag.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "photo-bytes".getBytes()
        );

        when(foundItemRepository.findById(id)).thenReturn(Optional.of(existingItem));
        when(photoStorage.store(any())).thenReturn("found-items/2026/05/generated.jpg");
        when(foundItemRepository.save(any(FoundItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<FoundItemResponse> response =
                service.updateFoundItemPhoto(id, photo, staffJwt(venueId));

        assertTrue(response.isPresent());
        assertEquals("found-items/2026/05/generated.jpg", response.get().photoKey());
        verify(photoStorage).delete("photo-123");
        verify(foundItemRepository).save(existingItem);
        verify(eventPublisher).publishFoundItemUpdated(existingItem);
    }

    @Test
    void updateFoundItemPhoto_shouldStillReturnResponseWhenPreviousPhotoDeletionFails() {
        FoundItemService service = service();
        Logger logger = (Logger) LoggerFactory.getLogger(FoundItemService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        FoundItem existingItem = foundItem(venueId);
        MockMultipartFile photo = new MockMultipartFile(
                "photo",
                "bag.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "photo-bytes".getBytes()
        );

        when(foundItemRepository.findById(id)).thenReturn(Optional.of(existingItem));
        when(photoStorage.store(any())).thenReturn("found-items/2026/05/generated.jpg");
        doThrow(new PhotoStorageException("delete failed")).when(photoStorage).delete("photo-123");
        when(foundItemRepository.save(any(FoundItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<FoundItemResponse> response;
        try {
            response = service.updateFoundItemPhoto(id, photo, staffJwt(venueId));
        } finally {
            logger.detachAppender(appender);
        }

        assertTrue(response.isPresent());
        assertEquals("found-items/2026/05/generated.jpg", response.get().photoKey());
        verify(foundItemRepository).save(existingItem);
        assertTrue(appender.list.stream().anyMatch(event ->
                event.getLevel() == Level.WARN
                        && event.getFormattedMessage().contains("Could not delete photo photo-123")
        ));
    }

    @Test
    void getFoundItemPhotoUrl_shouldReturnSignedUrlForStoredPhoto() {
        FoundItemService service = service();

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        URI signedUrl = URI.create("http://localhost:9000/foundflow-found-photos/photo-123?signature=test");

        when(foundItemRepository.findById(id)).thenReturn(Optional.of(foundItem(venueId)));
        when(photoStorage.signedUrl(eq("photo-123"), eq(Duration.ofMinutes(10)))).thenReturn(signedUrl);

        Optional<PhotoUrlResponse> response = service.getFoundItemPhotoUrl(id, staffJwt(venueId));

        assertTrue(response.isPresent());
        assertEquals(signedUrl, response.get().url());
    }

    @Test
    void getFoundItemPhotoUrl_shouldReturnNotImplementedWhenStorageDoesNotSupportSignedUrls() {
        FoundItemService service = service();

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        when(foundItemRepository.findById(id)).thenReturn(Optional.of(foundItem(venueId)));
        when(photoStorage.signedUrl(eq("photo-123"), eq(Duration.ofMinutes(10))))
                .thenThrow(new UnsupportedOperationException("not supported"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getFoundItemPhotoUrl(id, staffJwt(venueId))
        );

        assertEquals(501, exception.getStatusCode().value());
    }

    private FoundItemService service() {
        return new FoundItemService(
                foundItemRepository,
                venueAccessService,
                photoStorage,
                Duration.ofMinutes(10),
                eventPublisher,
                attributeExtractionService
        );
    }

    private CreateFoundItemRequest createRequest(UUID venueId, UUID reporterId) {
        return new CreateFoundItemRequest(
                "Schwarzer Rucksack",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                venueId,
                reporterId
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
