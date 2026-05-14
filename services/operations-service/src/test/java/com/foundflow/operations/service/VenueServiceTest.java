package com.foundflow.operations.service;

import com.foundflow.operations.domain.Venue;
import com.foundflow.operations.dto.CreateVenueRequest;
import com.foundflow.operations.dto.UpdateVenueRequest;
import com.foundflow.operations.dto.VenueResponse;
import com.foundflow.operations.repository.VenueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VenueServiceTest {

    @Mock
    private VenueRepository venueRepository;

    @Test
    void createVenue_shouldSaveAndReturnVenue() {
        VenueService venueService = new VenueService(venueRepository);

        CreateVenueRequest request = new CreateVenueRequest(
                "Chaos Arena",
                "friendly",
                "de"
        );

        when(venueRepository.save(any(Venue.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VenueResponse response = venueService.createVenue(request);

        ArgumentCaptor<Venue> captor = ArgumentCaptor.forClass(Venue.class);
        verify(venueRepository).save(captor.capture());

        Venue savedVenue = captor.getValue();

        assertEquals("Chaos Arena", savedVenue.getName());
        assertEquals("friendly", savedVenue.getTone());
        assertEquals("de", savedVenue.getDefaultLanguage());

        assertEquals("Chaos Arena", response.name());
        assertEquals("friendly", response.tone());
        assertEquals("de", response.defaultLanguage());
    }

    @Test
    void getVenueById_shouldReturnResponseWhenVenueExists() {
        VenueService venueService = new VenueService(venueRepository);

        UUID id = UUID.randomUUID();

        Venue venue = new Venue(
                "Chaos Arena",
                "friendly",
                "de"
        );

        when(venueRepository.findById(id)).thenReturn(Optional.of(venue));

        Optional<VenueResponse> response = venueService.getVenueById(id);

        assertTrue(response.isPresent());
        assertEquals("Chaos Arena", response.get().name());
        assertEquals("friendly", response.get().tone());
        assertEquals("de", response.get().defaultLanguage());

        verify(venueRepository).findById(id);
    }

    @Test
    void getVenueById_shouldReturnEmptyWhenVenueDoesNotExist() {
        VenueService venueService = new VenueService(venueRepository);

        UUID id = UUID.randomUUID();

        when(venueRepository.findById(id)).thenReturn(Optional.empty());

        Optional<VenueResponse> response = venueService.getVenueById(id);

        assertTrue(response.isEmpty());
        verify(venueRepository).findById(id);
    }

    @Test
    void getAllVenues_shouldReturnMappedResponses() {
        VenueService venueService = new VenueService(venueRepository);

        Venue venue1 = new Venue("Venue A", "formal", "de");
        Venue venue2 = new Venue("Venue B", "casual", "en");

        when(venueRepository.findAll()).thenReturn(List.of(venue1, venue2));

        List<VenueResponse> responses = venueService.getAllVenues();

        assertEquals(2, responses.size());
        assertEquals("Venue A", responses.get(0).name());
        assertEquals("Venue B", responses.get(1).name());

        verify(venueRepository).findAll();
    }

    @Test
    void updateVenue_shouldUpdateExistingVenue() {
        VenueService venueService = new VenueService(venueRepository);

        UUID id = UUID.randomUUID();

        Venue existingVenue = new Venue(
                "Old Venue",
                "old-tone",
                "de"
        );

        UpdateVenueRequest request = new UpdateVenueRequest(
                "Updated Venue",
                "professional",
                "en"
        );

        when(venueRepository.findById(id)).thenReturn(Optional.of(existingVenue));
        when(venueRepository.save(any(Venue.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<VenueResponse> response = venueService.updateVenue(id, request);

        assertTrue(response.isPresent());
        assertEquals("Updated Venue", response.get().name());
        assertEquals("professional", response.get().tone());
        assertEquals("en", response.get().defaultLanguage());

        verify(venueRepository).findById(id);
        verify(venueRepository).save(existingVenue);
    }
}