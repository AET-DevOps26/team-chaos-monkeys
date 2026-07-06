package com.foundflow.matching.client;

import com.foundflow.matching.dto.PublicFoundItemResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@Component
public class FoundItemClient {

    private final RestClient restClient;
    private final String internalToken;

    public FoundItemClient(
            RestClient.Builder restClientBuilder,
            @Value("${foundflow.services.found-item.base-url:http://found-item-service:8083}")
            String baseUrl,
            @Value("${foundflow.internal.token}")
            String internalToken
    ) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalStateException("foundflow.internal.token must be configured.");
        }
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .build();
        this.internalToken = internalToken;
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

    public PublicFoundItemResponse getPublicFoundItemDetail(UUID id, UUID venueId) {
        try {
            PublicFoundItemResponse response = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/found-items/{id}/public-detail")
                            .queryParam("venueId", venueId)
                            .build(id))
                    .header("X-FoundFlow-Internal-Token", internalToken)
                    .retrieve()
                    .body(PublicFoundItemResponse.class);

            if (response == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Found item service returned no public detail."
                );
            }

            return response;
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Public found item detail does not exist.");
        } catch (HttpClientErrorException exception) {
            throw new ResponseStatusException(
                    exception.getStatusCode(),
                    "Could not load public found item detail.",
                    exception
            );
        }
    }

    public RemotePhoto getPublicFoundItemPhoto(UUID id, UUID venueId) {
        try {
            ResponseEntity<byte[]> response = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/found-items/{id}/photo")
                            .queryParam("venueId", venueId)
                            .build(id))
                    .header("X-FoundFlow-Internal-Token", internalToken)
                    .retrieve()
                    .toEntity(byte[].class);

            byte[] body = response.getBody();
            if (body == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Found item service returned no photo body."
                );
            }

            MediaType contentType = Optional.ofNullable(response.getHeaders().getContentType())
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);
            long sizeBytes = response.getHeaders().getContentLength();
            return new RemotePhoto(body, contentType, sizeBytes >= 0 ? sizeBytes : body.length);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Public found item photo does not exist.");
        } catch (HttpClientErrorException exception) {
            throw new ResponseStatusException(
                    exception.getStatusCode(),
                    "Could not load public found item photo.",
                    exception
            );
        }
    }
}
