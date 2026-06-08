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
import com.foundflow.founditem.dto.PublicFoundItemResponse;
import com.foundflow.founditem.dto.UpdateFoundItemRequest;
import com.foundflow.founditem.messaging.FoundItemEventPublisher;
import com.foundflow.founditem.repository.BucketCountView;
import com.foundflow.founditem.repository.FoundItemRepository;
import com.foundflow.founditem.security.VenueAccessService;
import com.foundflow.genai.client.AttributeExtractionService;
import com.foundflow.genai.client.ExtractionResult;
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
import org.springframework.test.util.ReflectionTestUtils;
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
    void createFoundItem_shouldUseVenueFromJwtForStaff() {
        FoundItemService service = service();

        UUID jwtVenueId = UUID.randomUUID();
        UUID requestVenueId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        LocalDateTime foundAt = LocalDateTime.of(2026, 5, 12, 14, 30);

        CreateFoundItemRequest request = new CreateFoundItemRequest(
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
        assertNull(captor.getValue().getPhotoKey());
        assertEquals(jwtVenueId, response.venueId());
        assertNull(response.photoKey());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void createFoundItem_shouldUseJwtUserIdWhenReporterIdIsMissing() {
        FoundItemService service = service();

        UUID jwtVenueId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        CreateFoundItemRequest request = new CreateFoundItemRequest(
                "Schwarzer Rucksack",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                "Neben Buehne 2",
                UUID.randomUUID(),
                null,
                new ItemAttributesDto("Bag", "Nike", "Black", List.of("Roter Anhaenger"))
        );

        when(foundItemRepository.save(any(FoundItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FoundItemResponse response = service.createFoundItem(request, staffJwt(jwtVenueId, userId));

        ArgumentCaptor<FoundItem> captor = ArgumentCaptor.forClass(FoundItem.class);
        verify(foundItemRepository).save(captor.capture());

        assertEquals(userId, captor.getValue().getReporterId());
        assertEquals(userId, response.reporterId());
    }

    @Test
    void createFoundItem_shouldPersistWithoutPhotoKey() {
        FoundItemService service = service();

        UUID jwtVenueId = UUID.randomUUID();
        UUID requestVenueId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        CreateFoundItemRequest request = new CreateFoundItemRequest(
                "Schwarzer Rucksack",
                LocalDateTime.of(2026, 5, 12, 14, 30),
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

        assertNull(captor.getValue().getPhotoKey());
        assertNull(response.photoKey());
    }

    @Test
    void updateFoundItemPhoto_shouldPopulateLocationFromGenAiWhenAvailable() {
        FoundItemService service = service();

        UUID jwtVenueId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        FoundItem existingItem = new FoundItem(
                null,
                "Schwarzer Rucksack neben Buehne 2",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                null,
                ItemStatus.STORED,
                jwtVenueId,
                reporterId,
                new ItemAttributes(null, null, null, List.of())
        );
        MockMultipartFile photo = new MockMultipartFile(
                "photo",
                "bag.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "photo-bytes".getBytes()
        );

        ItemAttributes extractedAttrs = new ItemAttributes("Bag", null, "Black", List.of());

        when(foundItemRepository.findById(id)).thenReturn(Optional.of(existingItem));
        when(photoStorage.store(any())).thenReturn("found-items/2026/05/generated.jpg");
        when(foundItemRepository.save(any(FoundItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(attributeExtractionService.extractWithLocation(any(), any()))
                .thenReturn(Optional.of(new ExtractionResult(extractedAttrs, "neben Buehne 2")));

        Optional<FoundItemResponse> response = service.updateFoundItemPhoto(id, photo, staffJwt(jwtVenueId));

        ArgumentCaptor<FoundItem> captor = ArgumentCaptor.forClass(FoundItem.class);
        verify(foundItemRepository, times(2)).save(captor.capture());
        FoundItem persisted = captor.getValue();
        assertTrue(response.isPresent());
        assertEquals("found-items/2026/05/generated.jpg", response.get().photoKey());
        assertEquals("neben Buehne 2", persisted.getLocationHint());
        assertEquals("Bag", persisted.getAttributes().getCategory());
        verify(eventPublisher).publishFoundItemCreated(existingItem);
    }

    @Test
    void updateFoundItemPhoto_shouldLeaveLocationNullWhenGenAiFindsNone() {
        FoundItemService service = service();

        UUID jwtVenueId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        FoundItem existingItem = new FoundItem(
                null,
                "Found a wallet",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                null,
                ItemStatus.STORED,
                jwtVenueId,
                reporterId,
                new ItemAttributes(null, null, null, List.of())
        );
        MockMultipartFile photo = new MockMultipartFile(
                "photo",
                "wallet.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "photo-bytes".getBytes()
        );

        ItemAttributes extractedAttrs = new ItemAttributes("Wallet", null, "Brown", List.of());

        when(foundItemRepository.findById(id)).thenReturn(Optional.of(existingItem));
        when(photoStorage.store(any())).thenReturn("found-items/2026/05/generated.jpg");
        when(foundItemRepository.save(any(FoundItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(attributeExtractionService.extractWithLocation(any(), any()))
                .thenReturn(Optional.of(new ExtractionResult(extractedAttrs, null)));

        service.updateFoundItemPhoto(id, photo, staffJwt(jwtVenueId));

        ArgumentCaptor<FoundItem> captor = ArgumentCaptor.forClass(FoundItem.class);
        verify(foundItemRepository, times(2)).save(captor.capture());
        assertNull(captor.getValue().getLocationHint());
        verify(eventPublisher).publishFoundItemCreated(existingItem);
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
        UUID reporterId = UUID.randomUUID();
        when(foundItemRepository.findDailyBuckets(venueId, null, reporterId)).thenReturn(List.of(
                bucket(java.time.LocalDate.of(2026, 5, 19), 2),
                bucket(java.time.LocalDate.of(2026, 5, 20), 1)
        ));

        var histogram = service.getFoundItemHistogram(null, null, reporterId, staffJwt(venueId));

        assertEquals(2, histogram.perDay().size());
        assertEquals(java.time.LocalDate.of(2026, 5, 19), histogram.perDay().get(0).bucketStart());
        assertEquals(2, histogram.perDay().get(0).count());
        assertEquals(1, histogram.perWeek().size());
        assertEquals(java.time.LocalDate.of(2026, 5, 18), histogram.perWeek().get(0).bucketStart());
        assertEquals(3, histogram.perMonth().get(0).count());
        verify(foundItemRepository).findDailyBuckets(venueId, null, reporterId);
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
        verify(eventPublisher, never()).publishFoundItemCreated(any());
    }

    @Test
    void updateFoundItem_shouldNotPublishUpdateBeforePhotoUpload() {
        FoundItemService service = service();

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        FoundItem existingItem = new FoundItem(
                null,
                "Schwarzer Rucksack",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                "Neben Buehne 2",
                ItemStatus.STORED,
                venueId,
                UUID.randomUUID(),
                new ItemAttributes("Bag", "Nike", "Black", List.of("Roter Anhaenger"))
        );

        when(foundItemRepository.findById(id)).thenReturn(Optional.of(existingItem));
        when(foundItemRepository.save(any(FoundItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateFoundItemRequest request = new UpdateFoundItemRequest(
                "Neue Beschreibung",
                LocalDateTime.of(2026, 5, 12, 18, 45),
                "Neuer Ort",
                ItemStatus.RESERVED,
                venueId,
                UUID.randomUUID(),
                new ItemAttributesDto("Bag", "Adidas", "Blue", List.of("Neues Merkmal"))
        );

        Optional<FoundItemResponse> response =
                service.updateFoundItem(id, request, staffJwt(venueId));

        assertTrue(response.isPresent());
        assertNull(response.get().photoKey());
        verify(eventPublisher, never()).publishFoundItemUpdated(any());
        verify(eventPublisher, never()).publishFoundItemCreated(any());
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

    @Test
    void getPublicFoundItemDetail_shouldReturnLimitedInfoAndSignedUrlForMatchingVenue() {
        FoundItemService service = service();

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        URI signedUrl = URI.create("http://localhost:9000/foundflow-found-photos/photo-123?signature=test");
        FoundItem foundItem = foundItem(venueId);
        ReflectionTestUtils.setField(foundItem, "id", id);

        when(foundItemRepository.findById(id)).thenReturn(Optional.of(foundItem));
        when(photoStorage.signedUrl(eq("photo-123"), eq(Duration.ofMinutes(10)))).thenReturn(signedUrl);

        Optional<PublicFoundItemResponse> response = service.getPublicFoundItemDetail(id, venueId);

        assertTrue(response.isPresent());
        assertEquals(id, response.get().id());
        assertEquals("Schwarzer Rucksack", response.get().description());
        assertEquals("Neben Buehne 2", response.get().locationHint());
        assertEquals("Bag", response.get().attributes().category());
        assertEquals(signedUrl, response.get().photoUrl());
    }

    @Test
    void getPublicFoundItemDetail_shouldReturnEmptyForWrongVenue() {
        FoundItemService service = service();

        UUID id = UUID.randomUUID();

        when(foundItemRepository.findById(id)).thenReturn(Optional.of(foundItem(UUID.randomUUID())));

        Optional<PublicFoundItemResponse> response = service.getPublicFoundItemDetail(id, UUID.randomUUID());

        assertTrue(response.isEmpty());
        verify(photoStorage, never()).signedUrl(any(), any());
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
                "Neben Buehne 2",
                venueId,
                reporterId,
                new ItemAttributesDto("Bag", "Nike", "Black", List.of("Roter Anhaenger"))
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

    private Jwt staffJwt(UUID venueId, UUID userId) {
        return Jwt.withTokenValue("token")
                .subject(userId.toString())
                .header("alg", "none")
                .claim("user_id", userId.toString())
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
