package com.foundflow.lostitem.service;

import com.foundflow.common.domain.ItemAttributes;
import com.foundflow.lostitem.domain.LostReport;
import com.foundflow.lostitem.domain.ReportStatus;
import com.foundflow.lostitem.dto.CreateLostReportRequest;
import com.foundflow.lostitem.dto.ItemAttributesDto;
import com.foundflow.lostitem.dto.LostReportResponse;
import com.foundflow.lostitem.dto.PhotoUrlResponse;
import com.foundflow.lostitem.dto.UpdateLostReportRequest;
import com.foundflow.lostitem.repository.BucketCountView;
import com.foundflow.lostitem.repository.LostReportRepository;
import com.foundflow.lostitem.security.VenueAccessService;
import com.foundflow.photo.storage.PhotoStorageException;
import com.foundflow.photo.storage.PhotoStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;

import java.net.URI;
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

    private final VenueAccessService venueAccessService = new VenueAccessService();

    @Test
    void createLostReport_shouldUseVenueFromJwtForStaff() {
        LostReportService service = new LostReportService(lostReportRepository, venueAccessService, photoStorage);

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
    }

    @Test
    void createLostReport_shouldUseRequestVenueForPublicReport() {
        LostReportService service = new LostReportService(lostReportRepository, venueAccessService, photoStorage);

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
    }

    @Test
    void createLostReportWithPhoto_shouldPersistGeneratedPhotoKey() {
        LostReportService service = new LostReportService(lostReportRepository, venueAccessService, photoStorage);

        UUID venueId = UUID.randomUUID();
        MockMultipartFile photo = new MockMultipartFile(
                "photo",
                "bag.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "photo-bytes".getBytes()
        );
        CreateLostReportRequest request = createRequest(venueId);

        when(photoStorage.store(any())).thenReturn("lost-reports/2026/05/generated.jpg");
        when(lostReportRepository.save(any(LostReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LostReportResponse response = service.createLostReport(request, photo, null);

        ArgumentCaptor<LostReport> captor = ArgumentCaptor.forClass(LostReport.class);
        verify(lostReportRepository).save(captor.capture());

        assertEquals("lost-reports/2026/05/generated.jpg", captor.getValue().getPhotoKey());
        assertEquals("lost-reports/2026/05/generated.jpg", response.photoKey());
    }

    @Test
    void getLostReportById_shouldReturnResponseForOwnVenue() {
        LostReportService service = new LostReportService(lostReportRepository, venueAccessService, photoStorage);

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
        LostReportService service = new LostReportService(lostReportRepository, venueAccessService, photoStorage);

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
        LostReportService service = new LostReportService(lostReportRepository, venueAccessService, photoStorage);

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
        LostReportService service = new LostReportService(lostReportRepository, venueAccessService, photoStorage);

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
    }

    @Test
    void updateLostReportPhoto_shouldStorePhotoAndSaveGeneratedKey() {
        LostReportService service = new LostReportService(lostReportRepository, venueAccessService, photoStorage);

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
    }

    @Test
    void updateLostReportPhoto_shouldStillReturnResponseWhenPreviousPhotoDeletionFails() {
        LostReportService service = new LostReportService(lostReportRepository, venueAccessService, photoStorage);

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

        Optional<LostReportResponse> response =
                service.updateLostReportPhoto(id, photo, staffJwt(venueId));

        assertTrue(response.isPresent());
        assertEquals("lost-reports/2026/05/generated.jpg", response.get().photoKey());
        verify(lostReportRepository).save(existingReport);
    }

    @Test
    void getLostReportPhotoUrl_shouldReturnSignedUrlForStoredPhoto() {
        LostReportService service = new LostReportService(lostReportRepository, venueAccessService, photoStorage);

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        URI signedUrl = URI.create("http://localhost:9000/foundflow-lost-photos/photo-123?signature=test");

        when(lostReportRepository.findById(id)).thenReturn(Optional.of(lostReport(venueId)));
        when(photoStorage.signedUrl(eq("photo-123"), any())).thenReturn(signedUrl);

        Optional<PhotoUrlResponse> response = service.getLostReportPhotoUrl(id, staffJwt(venueId));

        assertTrue(response.isPresent());
        assertEquals(signedUrl, response.get().url());
    }

    private CreateLostReportRequest createRequest(UUID venueId) {
        return new CreateLostReportRequest(
                "Schwarzer Rucksack verloren",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                "Neben Buehne 2",
                venueId,
                "person@example.com",
                new ItemAttributesDto("Bag", "Nike", "Black", List.of("Roter Anhaenger"))
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
                new ItemAttributesDto("Bag", "Adidas", "Blue", List.of("Neues Merkmal"))
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
