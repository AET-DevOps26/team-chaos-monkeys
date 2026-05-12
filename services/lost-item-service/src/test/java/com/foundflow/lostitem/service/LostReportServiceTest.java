package com.foundflow.lostitem.service;

import com.foundflow.common.domain.ItemAttributes;
import com.foundflow.lostitem.domain.LostReport;
import com.foundflow.lostitem.domain.ReportStatus;
import com.foundflow.lostitem.dto.CreateLostReportRequest;
import com.foundflow.lostitem.dto.ItemAttributesDto;
import com.foundflow.lostitem.dto.LostReportResponse;
import com.foundflow.lostitem.dto.UpdateLostReportRequest;
import com.foundflow.lostitem.repository.LostReportRepository;
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
class LostReportServiceTest {

    @Mock
    private LostReportRepository lostReportRepository;

    @Test
    void createLostReport_shouldSaveReportWithOpenStatus() {
        LostReportService lostReportService = new LostReportService(lostReportRepository);

        CreateLostReportRequest request = new CreateLostReportRequest(
                "photo-123",
                "Schwarzer Rucksack verloren",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                "Neben Bühne 2",
                "person@example.com",
                new ItemAttributesDto(
                        "Bag",
                        "Nike",
                        "Black",
                        List.of("Roter Anhänger", "Kratzer vorne")
                )
        );

        when(lostReportRepository.save(any(LostReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LostReportResponse response = lostReportService.createLostReport(request);

        ArgumentCaptor<LostReport> captor = ArgumentCaptor.forClass(LostReport.class);
        verify(lostReportRepository).save(captor.capture());

        LostReport savedReport = captor.getValue();

        assertEquals("photo-123", savedReport.getPhotoKey());
        assertEquals("Schwarzer Rucksack verloren", savedReport.getDescription());
        assertEquals(LocalDateTime.of(2026, 5, 12, 14, 30), savedReport.getLostAt());
        assertEquals("Neben Bühne 2", savedReport.getLocation());
        assertEquals("person@example.com", savedReport.getContactEmail());
        assertEquals(ReportStatus.OPEN, savedReport.getStatus());

        assertNotNull(savedReport.getAttributes());
        assertEquals("Bag", savedReport.getAttributes().getCategory());
        assertEquals("Nike", savedReport.getAttributes().getBrand());
        assertEquals("Black", savedReport.getAttributes().getColor());
        assertEquals(List.of("Roter Anhänger", "Kratzer vorne"), savedReport.getAttributes().getMarks());

        assertEquals(ReportStatus.OPEN, response.status());
        assertEquals("Schwarzer Rucksack verloren", response.description());
    }

    @Test
    void getLostReportById_shouldReturnResponseWhenReportExists() {
        LostReportService lostReportService = new LostReportService(lostReportRepository);

        UUID id = UUID.randomUUID();

        LostReport lostReport = new LostReport(
                "photo-123",
                "Schwarzer Rucksack verloren",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                "Neben Bühne 2",
                ReportStatus.OPEN,
                "person@example.com",
                new ItemAttributes(
                        "Bag",
                        "Nike",
                        "Black",
                        List.of("Roter Anhänger")
                )
        );

        when(lostReportRepository.findById(id)).thenReturn(Optional.of(lostReport));

        Optional<LostReportResponse> response = lostReportService.getLostReportById(id);

        assertTrue(response.isPresent());
        assertEquals("Schwarzer Rucksack verloren", response.get().description());
        assertEquals(ReportStatus.OPEN, response.get().status());
        assertEquals("Nike", response.get().attributes().brand());

        verify(lostReportRepository).findById(id);
    }

    @Test
    void getLostReportById_shouldReturnEmptyWhenReportDoesNotExist() {
        LostReportService lostReportService = new LostReportService(lostReportRepository);

        UUID id = UUID.randomUUID();

        when(lostReportRepository.findById(id)).thenReturn(Optional.empty());

        Optional<LostReportResponse> response = lostReportService.getLostReportById(id);

        assertTrue(response.isEmpty());
        verify(lostReportRepository).findById(id);
    }

    @Test
    void updateLostReport_shouldUpdateExistingReport() {
        LostReportService lostReportService = new LostReportService(lostReportRepository);

        UUID id = UUID.randomUUID();

        LostReport existingReport = new LostReport(
                "old-photo",
                "Alte Beschreibung",
                LocalDateTime.of(2026, 5, 10, 10, 0),
                "Alter Ort",
                ReportStatus.OPEN,
                "old@example.com",
                new ItemAttributes(
                        "Old Category",
                        "Old Brand",
                        "Old Color",
                        List.of("Altes Merkmal")
                )
        );

        UpdateLostReportRequest request = new UpdateLostReportRequest(
                "new-photo",
                "Neue Beschreibung",
                LocalDateTime.of(2026, 5, 13, 9, 15),
                "Neuer Ort",
                ReportStatus.MATCHED,
                "new@example.com",
                new ItemAttributesDto(
                        "Bag",
                        "Adidas",
                        "Blue",
                        List.of("Neues Merkmal")
                )
        );

        when(lostReportRepository.findById(id)).thenReturn(Optional.of(existingReport));
        when(lostReportRepository.save(any(LostReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<LostReportResponse> response = lostReportService.updateLostReport(id, request);

        assertTrue(response.isPresent());
        assertEquals("Neue Beschreibung", response.get().description());
        assertEquals(ReportStatus.MATCHED, response.get().status());
        assertEquals("Adidas", response.get().attributes().brand());
        assertEquals("new@example.com", response.get().contactEmail());

        verify(lostReportRepository).findById(id);
        verify(lostReportRepository).save(existingReport);
    }
}