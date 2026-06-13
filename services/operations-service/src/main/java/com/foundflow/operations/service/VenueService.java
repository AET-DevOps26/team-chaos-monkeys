package com.foundflow.operations.service;

import com.foundflow.operations.domain.Venue;
import com.foundflow.operations.dto.CreateVenueRequest;
import com.foundflow.operations.dto.PublicVenueResponse;
import com.foundflow.operations.dto.UpdateVenueRequest;
import com.foundflow.operations.dto.VenueResponse;
import com.foundflow.operations.messaging.VenueEventPublisher;
import com.foundflow.operations.repository.VenueRepository;
import com.foundflow.operations.security.VenueAccessService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class VenueService {

    private final VenueRepository venueRepository;
    private final VenueAccessService venueAccessService;
    private final VenueEventPublisher venueEventPublisher;

    public VenueService(
            VenueRepository venueRepository,
            VenueAccessService venueAccessService,
            VenueEventPublisher venueEventPublisher
    ) {
        this.venueRepository = venueRepository;
        this.venueAccessService = venueAccessService;
        this.venueEventPublisher = venueEventPublisher;
    }

    public VenueResponse createVenue(
            CreateVenueRequest request,
            Jwt jwt
    ) {
        verifyAdmin(jwt);

        Venue venue = new Venue(
                request.name(),
                request.tone(),
                request.defaultLanguage()
        );

        Venue savedVenue = venueRepository.save(venue);
        return toResponse(savedVenue);
    }

    public List<VenueResponse> getAllVenues(Jwt jwt) {
        if (venueAccessService.isAdmin(jwt)) {
            return venueRepository.findAll()
                    .stream()
                    .map(this::toResponse)
                    .toList();
        }

        return venueRepository.findById(venueAccessService.getVenueId(jwt))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<PublicVenueResponse> getPublicVenues() {
        return venueRepository.findAll()
                .stream()
                .map(this::toPublicResponse)
                .toList();
    }

    public Optional<VenueResponse> getVenueById(
            UUID id,
            Jwt jwt
    ) {
        verifyVenueAccess(jwt, id);

        return venueRepository.findById(id)
                .map(this::toResponse);
    }

    public Optional<VenueResponse> updateVenue(
            UUID id,
            UpdateVenueRequest request,
            Jwt jwt
    ) {
        verifyVenueUpdateAccess(jwt, id);

        return venueRepository.findById(id)
                .map(venue -> {
                    venue.setName(request.name());
                    venue.setTone(request.tone());
                    venue.setDefaultLanguage(request.defaultLanguage());

                    Venue updatedVenue = venueRepository.save(venue);
                    return toResponse(updatedVenue);
                });
    }

    @Transactional
    public boolean deleteVenue(UUID id, Jwt jwt) {
        verifyAdmin(jwt);

        return venueRepository.findById(id)
                .map(venue -> {
                    venueRepository.delete(venue);
                    publishVenueDeletedAfterCommit(id);
                    return true;
                })
                .orElse(false);
    }

    private void publishVenueDeletedAfterCommit(UUID id) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            venueEventPublisher.publishVenueDeleted(id);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                venueEventPublisher.publishVenueDeleted(id);
            }
        });
    }

    private void verifyVenueAccess(Jwt jwt, UUID venueId) {
        if (!venueAccessService.canAccessVenue(jwt, venueId)) {
            throw new AccessDeniedException("No access to this venue.");
        }
    }

    private void verifyVenueUpdateAccess(Jwt jwt, UUID venueId) {
        if (venueAccessService.isAdmin(jwt)) {
            return;
        }

        if (!venueAccessService.isOpsManager(jwt)
                || !venueAccessService.canAccessVenue(jwt, venueId)) {
            throw new AccessDeniedException("Only admins and ops managers can update venues.");
        }
    }

    private void verifyAdmin(Jwt jwt) {
        if (!venueAccessService.isAdmin(jwt)) {
            throw new AccessDeniedException("Only admins can perform this operation.");
        }
    }

    private VenueResponse toResponse(Venue venue) {
        return new VenueResponse(
                venue.getId(),
                venue.getName(),
                venue.getTone(),
                venue.getDefaultLanguage()
        );
    }

    private PublicVenueResponse toPublicResponse(Venue venue) {
        return new PublicVenueResponse(
                venue.getId(),
                venue.getName()
        );
    }
}
