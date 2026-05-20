package com.foundflow.lostitem.service;

import com.foundflow.common.domain.ItemAttributes;
import com.foundflow.lostitem.domain.LostReport;
import com.foundflow.lostitem.domain.ReportStatus;
import com.foundflow.lostitem.dto.CreateLostReportRequest;
import com.foundflow.lostitem.dto.ItemAttributesDto;
import com.foundflow.lostitem.dto.LostReportResponse;
import com.foundflow.lostitem.dto.UpdateLostReportRequest;
import com.foundflow.lostitem.repository.BucketCountView;
import com.foundflow.lostitem.repository.LostReportRepository;
import com.foundflow.lostitem.security.VenueAccessService;
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
class LostReportServiceTest {

    @Mock
    private LostReportRepository lostReportRepository;

    private final VenueAccessService venueAccessService = new VenueAccessService();

    @Test
    void createLostReport_shouldUseVenueFromJwtForStaff() {
        LostReportService service = new LostReportService(lostReportRepository, venueAccessService);

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
        LostReportService service = new LostReportService(lostReportRepository, venueAccessService);

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
    void getLostReportById_shouldReturnResponseForOwnVenue() {
        LostReportService service = new LostReportService(lostReportRepository, venueAccessService);

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
        LostReportService service = new LostReportService(lostReportRepository, venueAccessService);

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
        LostReportService service = new LostReportService(lostReportRepository, venueAccessService);

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
        LostReportService service = new LostReportService(lostReportRepository, venueAccessService);

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
    }

    private CreateLostReportRequest createRequest(UUID venueId) {
        return new CreateLostReportRequest(
                "photo-123",
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
                "photo-456",
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
