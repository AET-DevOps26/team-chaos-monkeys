package com.foundflow.operations.client;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.UUID;

/**
 * Validates that a client-supplied venueId refers to a real venue in
 * operations-service before an item is persisted. Matching is venue-scoped, so
 * an item logged into a non-existent venue could never match. Reads the
 * unauthenticated {@code /api/venues/public} list, so the same check covers both
 * admin (JWT) and guest (no-JWT) intake. Shared by lost-item-service and
 * found-item-service.
 */
public class OperationsVenueClient {

    private final RestClient restClient;

    public OperationsVenueClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public void requireExisting(UUID venueId) {
        if (venueId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "venueId is required.");
        }
        if (!exists(venueId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Venue does not exist.");
        }
    }

    private boolean exists(UUID venueId) {
        PublicVenue[] venues;
        try {
            venues = restClient.get()
                    .uri("/api/venues/public")
                    .retrieve()
                    .body(PublicVenue[].class);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not validate venue against operations-service.",
                    exception
            );
        }
        return venues != null && Arrays.stream(venues).anyMatch(venue -> venueId.equals(venue.id()));
    }

    private record PublicVenue(UUID id, String name) {
    }
}
