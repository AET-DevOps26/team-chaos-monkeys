package com.foundflow.lostitem.service;

import com.foundflow.common.domain.ItemAttributes;
import com.foundflow.lostitem.domain.LostReport;
import com.foundflow.lostitem.domain.ReportStatus;
import com.foundflow.lostitem.dto.CreateLostReportRequest;
import com.foundflow.lostitem.dto.ItemAttributesDto;
import com.foundflow.lostitem.dto.LostReportResponse;
import com.foundflow.lostitem.dto.UpdateLostReportRequest;
import com.foundflow.lostitem.repository.LostReportRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LostReportService {

    private final LostReportRepository lostReportRepository;

    public LostReportService(LostReportRepository lostReportRepository) {
        this.lostReportRepository = lostReportRepository;
    }

    public LostReportResponse createLostReport(CreateLostReportRequest request) {
        LostReport lostReport = new LostReport(
                request.photoKey(),
                request.description(),
                request.lostAt(),
                request.location(),
                ReportStatus.OPEN,
                request.contactEmail(),
                toItemAttributes(request.attributes())
        );

        LostReport savedLostReport = lostReportRepository.save(lostReport);
        return toResponse(savedLostReport);
    }

    public List<LostReportResponse> getAllLostReports() {
        return lostReportRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public Optional<LostReportResponse> getLostReportById(UUID id) {
        return lostReportRepository.findById(id)
                .map(this::toResponse);
    }

    public Optional<LostReportResponse> updateLostReport(
            UUID id,
            UpdateLostReportRequest request
    ) {
        return lostReportRepository.findById(id)
                .map(existingReport -> {
                    existingReport.setPhotoKey(request.photoKey());
                    existingReport.setDescription(request.description());
                    existingReport.setLostAt(request.lostAt());
                    existingReport.setLocation(request.location());
                    existingReport.setStatus(request.status());
                    existingReport.setContactEmail(request.contactEmail());
                    existingReport.setAttributes(toItemAttributes(request.attributes()));

                    LostReport updatedReport = lostReportRepository.save(existingReport);
                    return toResponse(updatedReport);
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

    private LostReportResponse toResponse(LostReport lostReport) {
        return new LostReportResponse(
                lostReport.getId(),
                lostReport.getPhotoKey(),
                lostReport.getDescription(),
                lostReport.getLostAt(),
                lostReport.getLocation(),
                lostReport.getStatus(),
                lostReport.getContactEmail(),
                toItemAttributesDto(lostReport.getAttributes())
        );
    }
}