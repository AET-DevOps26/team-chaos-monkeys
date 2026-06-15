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
public class LostItemClient {

    private final RestClient restClient;

    public LostItemClient(
            @Value("${foundflow.services.lost-item.base-url:http://lost-item-service:8082}")
            String baseUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public ItemVenueReference getLostItem(UUID id, Jwt jwt) {
        LostReportContactReference reference = getLostReportContact(id, jwt);
        return new ItemVenueReference(reference.id(), reference.venueId());
    }

    public LostReportContactReference getLostReportContact(UUID id, Jwt jwt) {
        try {
            LostReportContactReference reference = restClient
                    .get()
                    .uri("/api/lost-items/{id}", id)
                    .headers(headers -> headers.setBearerAuth(jwt.getTokenValue()))
                    .retrieve()
                    .body(LostReportContactReference.class);

            if (reference == null || reference.venueId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Lost item service returned no venueId."
                );
            }

            return reference;
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lost item does not exist.");
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new AccessDeniedException("No access to this lost item.", exception);
        }
    }
}
