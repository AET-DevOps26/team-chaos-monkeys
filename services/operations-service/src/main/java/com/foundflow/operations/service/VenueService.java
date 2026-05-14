package com.foundflow.operations.service;

import com.foundflow.operations.domain.Venue;
import com.foundflow.operations.dto.CreateVenueRequest;
import com.foundflow.operations.dto.UpdateVenueRequest;
import com.foundflow.operations.dto.VenueResponse;
import com.foundflow.operations.repository.VenueRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class VenueService {

    private final VenueRepository venueRepository;

    public VenueService(VenueRepository venueRepository) {
        this.venueRepository = venueRepository;
    }

    public VenueResponse createVenue(CreateVenueRequest request) {
        Venue venue = new Venue(
                request.name(),
                request.tone(),
                request.defaultLanguage()
        );

        Venue savedVenue = venueRepository.save(venue);
        return toResponse(savedVenue);
    }

    public List<VenueResponse> getAllVenues() {
        return venueRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public Optional<VenueResponse> getVenueById(UUID id) {
        return venueRepository.findById(id)
                .map(this::toResponse);
    }

    public Optional<VenueResponse> updateVenue(
            UUID id,
            UpdateVenueRequest request
    ) {
        return venueRepository.findById(id)
                .map(venue -> {
                    venue.setName(request.name());
                    venue.setTone(request.tone());
                    venue.setDefaultLanguage(request.defaultLanguage());

                    Venue updatedVenue = venueRepository.save(venue);
                    return toResponse(updatedVenue);
                });
    }

    private VenueResponse toResponse(Venue venue) {
        return new VenueResponse(
                venue.getId(),
                venue.getName(),
                venue.getTone(),
                venue.getDefaultLanguage()
        );
    }
}