package com.foundflow.operations.service;

import com.foundflow.operations.domain.Venue;
import com.foundflow.operations.dto.CreateVenueRequest;
import com.foundflow.operations.dto.UpdateVenueRequest;
import com.foundflow.operations.dto.VenueResponse;
import com.foundflow.operations.messaging.VenueEventPublisher;
import com.foundflow.operations.repository.VenueRepository;
import com.foundflow.operations.security.VenueAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VenueServiceTest {

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private VenueEventPublisher venueEventPublisher;

    private final VenueAccessService venueAccessService = new VenueAccessService();

    @Test
    void createVenue_shouldSaveAndReturnVenueForAdmin() {
        VenueService service = new VenueService(venueRepository, venueAccessService, venueEventPublisher);

        CreateVenueRequest request = new CreateVenueRequest("Chaos Arena", "friendly", "de");

        when(venueRepository.save(any(Venue.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VenueResponse response = service.createVenue(request, adminJwt());

        ArgumentCaptor<Venue> captor = ArgumentCaptor.forClass(Venue.class);
        verify(venueRepository).save(captor.capture());

        assertEquals("Chaos Arena", captor.getValue().getName());
        assertEquals("Chaos Arena", response.name());
    }

    @Test
    void getAllVenues_shouldReturnOnlyOwnVenueForStaff() {
        VenueService service = new VenueService(venueRepository, venueAccessService, venueEventPublisher);

        UUID venueId = UUID.randomUUID();
        when(venueRepository.findById(venueId))
                .thenReturn(Optional.of(new Venue("Venue A", "formal", "de")));

        List<VenueResponse> responses = service.getAllVenues(staffJwt(venueId));

        assertEquals(1, responses.size());
        assertEquals("Venue A", responses.get(0).name());
        verify(venueRepository).findById(venueId);
        verify(venueRepository, never()).findAll();
    }

    @Test
    void getVenueById_shouldReturnResponseWhenStaffAccessesOwnVenue() {
        VenueService service = new VenueService(venueRepository, venueAccessService, venueEventPublisher);

        UUID id = UUID.randomUUID();
        when(venueRepository.findById(id))
                .thenReturn(Optional.of(new Venue("Chaos Arena", "friendly", "de")));

        Optional<VenueResponse> response = service.getVenueById(id, staffJwt(id));

        assertTrue(response.isPresent());
        assertEquals("Chaos Arena", response.get().name());
        verify(venueRepository).findById(id);
    }

    @Test
    void updateVenue_shouldUpdateExistingVenueForOpsManagerOwnVenue() {
        VenueService service = new VenueService(venueRepository, venueAccessService, venueEventPublisher);

        UUID id = UUID.randomUUID();
        Venue existingVenue = new Venue("Old Venue", "old-tone", "de");
        UpdateVenueRequest request = new UpdateVenueRequest("Updated Venue", "professional", "en");

        when(venueRepository.findById(id)).thenReturn(Optional.of(existingVenue));
        when(venueRepository.save(any(Venue.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<VenueResponse> response = service.updateVenue(id, request, opsManagerJwt(id));

        assertTrue(response.isPresent());
        assertEquals("Updated Venue", response.get().name());
        assertEquals("professional", response.get().tone());
        verify(venueRepository).save(existingVenue);
    }

    @Test
    void updateVenue_shouldRejectStaffEvenForOwnVenue() {
        VenueService service = new VenueService(venueRepository, venueAccessService, venueEventPublisher);

        UUID id = UUID.randomUUID();
        UpdateVenueRequest request = new UpdateVenueRequest("Updated Venue", "professional", "en");

        assertThrows(
                AccessDeniedException.class,
                () -> service.updateVenue(id, request, staffJwt(id))
        );
        verify(venueRepository, never()).findById(any());
        verify(venueRepository, never()).save(any());
    }

    @Test
    void deleteVenue_shouldDeleteForAdmin() {
        VenueService service = new VenueService(venueRepository, venueAccessService, venueEventPublisher);

        UUID id = UUID.randomUUID();
        Venue venue = new Venue("Venue A", "formal", "de");
        when(venueRepository.findById(id)).thenReturn(Optional.of(venue));

        assertTrue(service.deleteVenue(id, adminJwt()));
        verify(venueRepository).delete(venue);
        verify(venueEventPublisher).publishVenueDeleted(id);
    }

    @Test
    void deleteVenue_shouldRejectStaff() {
        VenueService service = new VenueService(venueRepository, venueAccessService, venueEventPublisher);

        UUID id = UUID.randomUUID();

        assertThrows(AccessDeniedException.class, () -> service.deleteVenue(id, staffJwt(id)));
        verify(venueRepository, never()).findById(any());
        verify(venueRepository, never()).delete(any());
        verifyNoInteractions(venueEventPublisher);
    }

    private Jwt staffJwt(UUID venueId) {
        return roleJwt("STAFF", venueId);
    }

    private Jwt opsManagerJwt(UUID venueId) {
        return roleJwt("OPS_MANAGER", venueId);
    }

    private Jwt roleJwt(String role, UUID venueId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("roles", List.of(role))
                .claim("venue_id", venueId.toString())
                .build();
    }

    private Jwt adminJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("roles", List.of("ADMIN"))
                .build();
    }
}
