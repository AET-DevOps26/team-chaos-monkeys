package com.foundflow.matching.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class FoundItemClient {

    private final RestClient restClient;

    public FoundItemClient(
            @Value("${foundflow.services.found-item.base-url:http://found-item-service:8083}")
            String baseUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public ItemVenueReference getFoundItem(UUID id, Jwt jwt) {
        try {
            ItemVenueReference reference = restClient
                    .get()
                    .uri("/api/found-items/{id}", id)
                    .headers(headers -> headers.setBearerAuth(jwt.getTokenValue()))
                    .retrieve()
                    .body(ItemVenueReference.class);

            if (reference == null || reference.venueId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Found item service returned no venueId."
                );
            }

            return reference;
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Found item does not exist.");
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new AccessDeniedException("No access to this found item.", exception);
        }
    }
}
