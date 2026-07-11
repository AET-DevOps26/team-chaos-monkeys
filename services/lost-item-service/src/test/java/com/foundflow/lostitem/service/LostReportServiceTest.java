package com.foundflow.lostitem.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.foundflow.common.domain.ItemAttributes;
import com.foundflow.lostitem.domain.LostReport;
import com.foundflow.lostitem.domain.ReportStatus;
import com.foundflow.lostitem.dto.CreateLostReportRequest;
import com.foundflow.lostitem.dto.ItemAttributesDto;
import com.foundflow.lostitem.dto.LostReportResponse;
import com.foundflow.lostitem.dto.UpdateLostReportRequest;
import com.foundflow.operations.client.OperationsVenueClient;
import com.foundflow.lostitem.messaging.LostReportEventPublisher;
import com.foundflow.lostitem.repository.BucketCountView;
import com.foundflow.lostitem.repository.LostReportRepository;
import com.foundflow.lostitem.security.VenueAccessService;
import com.foundflow.genai.client.AttributeExtractionService;
import com.foundflow.genai.client.ExtractionResult;
import com.foundflow.photo.storage.PhotoData;
import com.foundflow.photo.storage.PhotoStorage;
import com.foundflow.photo.storage.PhotoStorageException;
import com.foundflow.photo.storage.PhotoUrlResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LostReportServiceTest {

    @Mock
    private LostReportRepository lostReportRepository;

    @Mock
    private PhotoStorage photoStorage;

    @Mock
    private LostReportEventPublisher eventPublisher;

    @Mock
    private AttributeExtractionService attributeExtractionService;

    @Mock
    private OperationsVenueClient operationsVenueClient;

    private final VenueAccessService venueAccessService = new VenueAccessService();

    @Test
    void createLostReport_shouldUseVenueFromJwtForStaff() {
        LostReportService service = service();

        UUID venueId = UUID.randomUUID();
        CreateLostReportRequest request = createRequest(UUID.randomUUID());

        when(lostReportRepository.save(any(LostReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LostReportResponse response = service.createLostReport(request, staffJwt(venueId));

        ArgumentCaptor<LostReport> captor = ArgumentCaptor.forClass(LostReport.class);
        verify(lostReportRepository).save(captor.capture());

        assertEquals(venueId, captor.getValue().getVenueId());
        assertEquals(ReportStatus.OPEN, captor.getValue().getStatus());
        assertEquals(venueId, response.venueId());
        verify(eventPublisher).publishLostReportCreated(captor.getValue());
    }

    @Test
    void createLostReport_shouldUseRequestVenueForPublicReport() {
        LostReportService service = service();

        UUID venueId = UUID.randomUUID();
        CreateLostReportRequest request = createRequest(venueId);

        when(lostReportRepository.save(any(LostReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LostReportResponse response = service.createLostReport(request, null);

        ArgumentCaptor<LostReport> captor = ArgumentCaptor.forClass(LostReport.class);
        verify(lostReportRepository).save(captor.capture());

        assertEquals(venueId, captor.getValue().getVenueId());
        assertEquals(ReportStatus.OPEN, captor.getValue().getStatus());
        assertEquals(venueId, response.venueId());
        verify(eventPublisher).publishLostReportCreated(captor.getValue());
        verify(operationsVenueClient).requireExisting(venueId);
    }

    @Test
    void createLostReport_shouldRejectUnknownVenueForPublicReport() {
        LostReportService service = service();

        UUID bogusVenueId = UUID.randomUUID();
        CreateLostReportRequest request = createRequest(bogusVenueId);
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Venue does not exist."))
                .when(operationsVenueClient).requireExisting(bogusVenueId);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.createLostReport(request, null)
        );

        assertEquals(400, exception.getStatusCode().value());
        verify(lostReportRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void createLostReport_shouldNotValidateVenueForStaff() {
        LostReportService service = service();

        when(lostReportRepository.save(any(LostReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.createLostReport(createRequest(UUID.randomUUID()), staffJwt(UUID.randomUUID()));

        verify(operationsVenueClient, never()).requireExisting(any());
    }

    @Test
    void createLostReport_shouldPersistWithoutPhotoKey() {
        LostReportService service = service();

        UUID venueId = UUID.randomUUID();
        CreateLostReportRequest request = createRequest(venueId);

        when(lostReportRepository.save(any(LostReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LostReportResponse response = service.createLostReport(request, null);

        ArgumentCaptor<LostReport> captor = ArgumentCaptor.forClass(LostReport.class);
        verify(lostReportRepository).save(captor.capture());

        assertNull(captor.getValue().getPhotoKey());
        assertNull(response.photoKey());
    }

    @Test
    void createLostReport_shouldExtractAttributesForTextOnlyReportWithoutPhoto() {
        LostReportService service = service();

        UUID venueId = UUID.randomUUID();
        CreateLostReportRequest request = new CreateLostReportRequest(
                "purple shirt",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                "near the cloakroom",
                venueId,
                "person@example.com",
                new ItemAttributesDto(null, null, null, null, List.of())
        );
        ItemAttributes extracted = new ItemAttributes(
                "CLOTHING", "purple cotton shirt", null, "purple", List.of());

        when(lostReportRepository.save(any(LostReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(attributeExtractionService.extractWithLocation(eq("purple shirt"), isNull()))
                .thenReturn(Optional.of(new ExtractionResult(extracted, "the bar")));

        service.createLostReport(request, null);

        // Text-only report (no photo) still triggers extraction with a null photoKey.
        verify(attributeExtractionService).extractWithLocation("purple shirt", null);

        ArgumentCaptor<LostReport> captor = ArgumentCaptor.forClass(LostReport.class);
        verify(lostReportRepository, times(2)).save(captor.capture());
        // The generated description is persisted so it rides the created event.
        assertEquals("CLOTHING", captor.getValue().getAttributes().getCategory());
        assertEquals("purple cotton shirt", captor.getValue().getAttributes().getDescription());
        // The guest typed a location, so the extracted one must not overwrite it.
        assertEquals("near the cloakroom", captor.getValue().getLocation());
        verify(eventPublisher).publishLostReportCreated(captor.getValue());
    }

    @Test
    void createLostReport_shouldPersistExtractedLocationWhenGuestSuppliedNone() {
        LostReportService service = service();

        UUID venueId = UUID.randomUUID();
        CreateLostReportRequest request = new CreateLostReportRequest(
                "left my purple sunglasses at the bar",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                null,
                venueId,
                "person@example.com",
                new ItemAttributesDto(null, null, null, null, List.of())
        );
        ItemAttributes extracted = new ItemAttributes(
                "ACCESSORIES", "purple sunglasses", null, "purple", List.of());

        when(lostReportRepository.save(any(LostReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(attributeExtractionService.extractWithLocation(any(), isNull()))
                .thenReturn(Optional.of(new ExtractionResult(extracted, "bar")));

        service.createLostReport(request, null);

        ArgumentCaptor<LostReport> captor = ArgumentCaptor.forClass(LostReport.class);
        verify(lostReportRepository, times(2)).save(captor.capture());
        // No user-supplied location -> the GenAI-recovered location is persisted.
        assertEquals("bar", captor.getValue().getLocation());
        verify(eventPublisher).publishLostReportCreated(captor.getValue());
    }

    @Test
    void getLostReportById_shouldReturnResponseForOwnVenue() {
        LostReportService service = service();

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        when(lostReportRepository.findById(id)).thenReturn(Optional.of(lostReport(venueId)));

        Optional<LostReportResponse> response = service.getLostReportById(id, staffJwt(venueId));

        assertTrue(response.isPresent());
        assertEquals(venueId, response.get().venueId());
        assertEquals(ReportStatus.OPEN, response.get().status());
    }

    @Test
    void getAllLostReports_shouldUseVenueRepositoryForStaff() {
        LostReportService service = service();

        UUID venueId = UUID.randomUUID();
        when(lostReportRepository.findByVenueIdAndStatus(venueId, ReportStatus.OPEN))
                .thenReturn(List.of(lostReport(venueId)));

        List<LostReportResponse> responses =
                service.getAllLostReports(ReportStatus.OPEN, staffJwt(venueId));

        assertEquals(1, responses.size());
        assertEquals(venueId, responses.get(0).venueId());
        verify(lostReportRepository).findByVenueIdAndStatus(venueId, ReportStatus.OPEN);
    }

    @Test
    void histogram_shouldBucketAccessibleLostReportsByDayWeekAndMonth() {
        LostReportService service = service();

        UUID venueId = UUID.randomUUID();
        when(lostReportRepository.findDailyBuckets(venueId, null)).thenReturn(List.of(
                bucket(java.time.LocalDate.of(2026, 5, 19), 1),
                bucket(java.time.LocalDate.of(2026, 5, 20), 1)
        ));

        var histogram = service.getLostReportHistogram(null, staffJwt(venueId));

        assertEquals(2, histogram.perDay().size());
        assertEquals(java.time.LocalDate.of(2026, 5, 19), histogram.perDay().get(0).bucketStart());
        assertEquals(1, histogram.perDay().get(0).count());
        assertEquals(java.time.LocalDate.of(2026, 5, 18), histogram.perWeek().get(0).bucketStart());
        assertEquals(2, histogram.perMonth().get(0).count());
    }

    @Test
    void updateLostReport_shouldKeepVenueForStaff() {
        LostReportService service = service();

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        LostReport existingReport = lostReport(venueId);
        UpdateLostReportRequest request = updateRequest(UUID.randomUUID());

        when(lostReportRepository.findById(id)).thenReturn(Optional.of(existingReport));
        when(lostReportRepository.save(any(LostReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<LostReportResponse> response =
                service.updateLostReport(id, request, staffJwt(venueId));

        assertTrue(response.isPresent());
        assertEquals(venueId, response.get().venueId());
        assertEquals(ReportStatus.MATCHED, response.get().status());
        assertEquals("photo-123", response.get().photoKey());
        ArgumentCaptor<LostReport> publishedReport = ArgumentCaptor.forClass(LostReport.class);
        verify(eventPublisher).publishLostReportUpdated(publishedReport.capture());
        assertSame(existingReport, publishedReport.getValue());
        assertEquals("Neue Beschreibung", publishedReport.getValue().getDescription());
        assertEquals(ReportStatus.MATCHED, publishedReport.getValue().getStatus());
        verify(eventPublisher, never()).publishLostReportCreated(any());
    }

    @Test
    void updateLostReportPhoto_shouldStorePhotoAndSaveGeneratedKey() {
        LostReportService service = service();

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        LostReport existingReport = lostReport(venueId);
        MockMultipartFile photo = new MockMultipartFile(
                "photo",
                "bag.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "photo-bytes".getBytes()
        );

        when(lostReportRepository.findById(id)).thenReturn(Optional.of(existingReport));
        when(photoStorage.store(any())).thenReturn("lost-reports/2026/05/generated.jpg");
        when(lostReportRepository.save(any(LostReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<LostReportResponse> response =
                service.updateLostReportPhoto(id, photo, staffJwt(venueId));

        assertTrue(response.isPresent());
        assertEquals("lost-reports/2026/05/generated.jpg", response.get().photoKey());
        verify(photoStorage).delete("photo-123");
        verify(lostReportRepository).save(existingReport);
        verify(eventPublisher).publishLostReportUpdated(existingReport);
    }

    @Test
    void updateLostReportPhoto_shouldAllowPublicInitialPhotoUploadAndExtractAttributes() {
        LostReportService service = service();

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        LostReport existingReport = new LostReport(
                null,
                "Schwarzer Rucksack verloren",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                "Neben Buehne 2",
                ReportStatus.OPEN,
                venueId,
                "person@example.com",
                new ItemAttributes(null, null, null, null, List.of())
        );
        MockMultipartFile photo = new MockMultipartFile(
                "photo",
                "bag.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "photo-bytes".getBytes()
        );
        ItemAttributes extractedAttributes = new ItemAttributes("Bag", null, null, "Black", List.of());

        when(lostReportRepository.findById(id)).thenReturn(Optional.of(existingReport));
        when(photoStorage.store(any())).thenReturn("lost-reports/2026/05/generated.jpg");
        when(lostReportRepository.save(any(LostReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(attributeExtractionService.extractWithLocation(any(), any()))
                .thenReturn(Optional.of(new ExtractionResult(extractedAttributes, null)));

        Optional<LostReportResponse> response = service.updateLostReportPhoto(id, photo, null);

        ArgumentCaptor<LostReport> captor = ArgumentCaptor.forClass(LostReport.class);
        verify(lostReportRepository, times(2)).save(captor.capture());
        assertTrue(response.isPresent());
        assertEquals("lost-reports/2026/05/generated.jpg", response.get().photoKey());
        assertEquals("Bag", captor.getValue().getAttributes().getCategory());
        verify(eventPublisher).publishLostReportUpdated(existingReport);
        verify(photoStorage, never()).delete(any());
    }

    @Test
    void updateLostReportPhoto_shouldRejectPublicPhotoReplacement() {
        LostReportService service = service();

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        MockMultipartFile photo = new MockMultipartFile(
                "photo",
                "bag.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "photo-bytes".getBytes()
        );

        when(lostReportRepository.findById(id)).thenReturn(Optional.of(lostReport(venueId)));

        assertThrows(
                AccessDeniedException.class,
                () -> service.updateLostReportPhoto(id, photo, null)
        );
        verify(photoStorage, never()).store(any());
        verify(lostReportRepository, never()).save(any());
    }

    @Test
    void updateLostReportPhoto_shouldStillReturnResponseWhenPreviousPhotoDeletionFails() {
        LostReportService service = service();
        Logger logger = (Logger) LoggerFactory.getLogger(LostReportService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        LostReport existingReport = lostReport(venueId);
        MockMultipartFile photo = new MockMultipartFile(
                "photo",
                "bag.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "photo-bytes".getBytes()
        );

        when(lostReportRepository.findById(id)).thenReturn(Optional.of(existingReport));
        when(photoStorage.store(any())).thenReturn("lost-reports/2026/05/generated.jpg");
        doThrow(new PhotoStorageException("delete failed")).when(photoStorage).delete("photo-123");
        when(lostReportRepository.save(any(LostReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<LostReportResponse> response;
        try {
            response = service.updateLostReportPhoto(id, photo, staffJwt(venueId));
        } finally {
            logger.detachAppender(appender);
        }

        assertTrue(response.isPresent());
        assertEquals("lost-reports/2026/05/generated.jpg", response.get().photoKey());
        verify(lostReportRepository).save(existingReport);
        assertTrue(appender.list.stream().anyMatch(event ->
                event.getLevel() == Level.WARN
                        && event.getFormattedMessage().contains("Could not delete photo photo-123")
        ));
    }

    @Test
    void getLostReportPhotoUrl_shouldReturnSignedUrlForStoredPhoto() {
        LostReportService service = service();

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        URI signedUrl = URI.create("http://localhost:9000/foundflow-lost-photos/photo-123?signature=test");

        when(lostReportRepository.findById(id)).thenReturn(Optional.of(lostReport(venueId)));
        when(photoStorage.signedUrl(eq("photo-123"), eq(Duration.ofMinutes(10)))).thenReturn(signedUrl);

        Optional<PhotoUrlResponse> response = service.getLostReportPhotoUrl(id, staffJwt(venueId));

        assertTrue(response.isPresent());
        assertEquals(signedUrl, response.get().url());
    }

    @Test
    void getLostReportPhoto_shouldRetrieveStoredPhoto() {
        LostReportService service = service();

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        PhotoData photo = new PhotoData(
                new ByteArrayInputStream("photo-bytes".getBytes()),
                MediaType.IMAGE_JPEG_VALUE,
                11
        );

        when(lostReportRepository.findById(id)).thenReturn(Optional.of(lostReport(venueId)));
        when(photoStorage.retrieve("photo-123")).thenReturn(photo);

        Optional<PhotoData> response = service.getLostReportPhoto(id, staffJwt(venueId));

        assertTrue(response.isPresent());
        assertSame(photo, response.get());
    }

    @Test
    void getLostReportPhoto_shouldReturnEmptyWhenReportDoesNotExist() {
        LostReportService service = service();

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        when(lostReportRepository.findById(id)).thenReturn(Optional.empty());

        assertTrue(service.getLostReportPhoto(id, staffJwt(venueId)).isEmpty());
    }

    @Test
    void getLostReportPhoto_shouldReturnNotFoundWhenReportHasNoPhoto() {
        LostReportService service = service();

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        LostReport lostReport = lostReport(venueId);
        lostReport.setPhotoKey(null);

        when(lostReportRepository.findById(id)).thenReturn(Optional.of(lostReport));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getLostReportPhoto(id, staffJwt(venueId))
        );

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void getLostReportPhotoUrl_shouldReturnNotImplementedWhenStorageDoesNotSupportSignedUrls() {
        LostReportService service = service();

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        when(lostReportRepository.findById(id)).thenReturn(Optional.of(lostReport(venueId)));
        when(photoStorage.signedUrl(eq("photo-123"), eq(Duration.ofMinutes(10))))
                .thenThrow(new UnsupportedOperationException("not supported"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getLostReportPhotoUrl(id, staffJwt(venueId))
        );

        assertEquals(501, exception.getStatusCode().value());
    }

    @Test
    void applyMatchStatusChange_shouldMoveOpenToMatchedWithoutRepublishing() {
        LostReportService service = service();
        UUID id = UUID.randomUUID();
        LostReport report = lostReport(UUID.randomUUID()); // OPEN
        when(lostReportRepository.findById(id)).thenReturn(Optional.of(report));
        when(lostReportRepository.save(any(LostReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.applyMatchStatusChange(id, "MATCHED");

        assertEquals(ReportStatus.MATCHED, report.getStatus());
        verify(lostReportRepository).save(report);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void applyMatchStatusChange_shouldReopenMatchedReport() {
        LostReportService service = service();
        UUID id = UUID.randomUUID();
        LostReport report = lostReport(UUID.randomUUID());
        report.setStatus(ReportStatus.MATCHED);
        when(lostReportRepository.findById(id)).thenReturn(Optional.of(report));
        when(lostReportRepository.save(any(LostReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.applyMatchStatusChange(id, "OPEN");

        assertEquals(ReportStatus.OPEN, report.getStatus());
        verify(lostReportRepository).save(report);
    }

    @Test
    void applyMatchStatusChange_shouldLeaveTerminalStatusUntouched() {
        LostReportService service = service();
        UUID id = UUID.randomUUID();
        LostReport report = lostReport(UUID.randomUUID());
        report.setStatus(ReportStatus.COLLECTED);
        when(lostReportRepository.findById(id)).thenReturn(Optional.of(report));

        service.applyMatchStatusChange(id, "MATCHED");

        assertEquals(ReportStatus.COLLECTED, report.getStatus());
        verify(lostReportRepository, never()).save(any());
    }

    private LostReportService service() {
        return new LostReportService(
                lostReportRepository,
                venueAccessService,
                photoStorage,
                Duration.ofMinutes(10),
                eventPublisher,
                attributeExtractionService,
                operationsVenueClient
        );
    }

    private CreateLostReportRequest createRequest(UUID venueId) {
        return new CreateLostReportRequest(
                "Schwarzer Rucksack verloren",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                "Neben Buehne 2",
                venueId,
                "person@example.com",
                new ItemAttributesDto("Bag", null, "Nike", "Black", List.of("Roter Anhaenger"))
        );
    }

    private UpdateLostReportRequest updateRequest(UUID venueId) {
        return new UpdateLostReportRequest(
                "Neue Beschreibung",
                LocalDateTime.of(2026, 5, 13, 9, 15),
                "Neuer Ort",
                ReportStatus.MATCHED,
                venueId,
                "new@example.com",
                new ItemAttributesDto("Bag", null, "Adidas", "Blue", List.of("Neues Merkmal"))
        );
    }

    private LostReport lostReport(UUID venueId) {
        return lostReport(venueId, LocalDateTime.of(2026, 5, 12, 14, 30));
    }

    private LostReport lostReport(UUID venueId, LocalDateTime lostAt) {
        return new LostReport(
                "photo-123",
                "Schwarzer Rucksack verloren",
                lostAt,
                "Neben Buehne 2",
                ReportStatus.OPEN,
                venueId,
                "person@example.com",
                new ItemAttributes("Bag", null, "Nike", "Black", List.of("Roter Anhaenger"))
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
