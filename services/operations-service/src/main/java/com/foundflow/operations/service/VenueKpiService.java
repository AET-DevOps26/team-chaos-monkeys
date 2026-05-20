package com.foundflow.operations.service;

import com.foundflow.operations.dto.VenueKpiResponse;
import com.foundflow.operations.security.VenueAccessService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

@Service
public class VenueKpiService {

    private final RestClient foundItemClient;
    private final RestClient lostItemClient;
    private final RestClient matchingClient;
    private final VenueAccessService venueAccessService;

    public VenueKpiService(
            VenueAccessService venueAccessService,
            @Value("${foundflow.services.found-item.base-url:http://found-item-service:8083}")
            String foundItemBaseUrl,
            @Value("${foundflow.services.lost-item.base-url:http://lost-item-service:8082}")
            String lostItemBaseUrl,
            @Value("${foundflow.services.matching.base-url:http://matching-service:8084}")
            String matchingBaseUrl
    ) {
        this.venueAccessService = venueAccessService;
        this.foundItemClient = RestClient.builder()
                .baseUrl(foundItemBaseUrl)
                .build();
        this.lostItemClient = RestClient.builder()
                .baseUrl(lostItemBaseUrl)
                .build();
        this.matchingClient = RestClient.builder()
                .baseUrl(matchingBaseUrl)
                .build();
    }

    public VenueKpiResponse getKpis(Jwt jwt) {
        UUID venueId = venueAccessService.isAdmin(jwt)
                ? null
                : venueAccessService.getVenueId(jwt);

        return getKpis(venueId, jwt);
    }

    public VenueKpiResponse getKpis(UUID venueId, Jwt jwt) {
        UUID effectiveVenueId;
        if (venueAccessService.isAdmin(jwt)) {
            effectiveVenueId = venueId;
        } else {
            UUID jwtVenueId = venueAccessService.getVenueId(jwt);
            if (venueId != null && !venueId.equals(jwtVenueId)) {
                throw new AccessDeniedException("No access to KPIs for venue " + venueId + ".");
            }
            effectiveVenueId = jwtVenueId;
        }

        return new VenueKpiResponse(
                effectiveVenueId,
                getCount(foundItemClient, "/api/found-items/count", effectiveVenueId, null, jwt),
                getCount(lostItemClient, "/api/lost-items/count", effectiveVenueId, null, jwt),
                getCount(matchingClient, "/api/matches/count", effectiveVenueId, null, jwt),
                getCount(matchingClient, "/api/matches/count", effectiveVenueId, "PENDING", jwt)
        );
    }

    private long getCount(
            RestClient client,
            String path,
            UUID venueId,
            String status,
            Jwt jwt
    ) {
        CountResponse response = client
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(path)
                        .queryParamIfPresent("venueId", Optional.ofNullable(venueId))
                        .queryParamIfPresent("status", Optional.ofNullable(status))
                        .build())
                .headers(headers -> headers.setBearerAuth(jwt.getTokenValue()))
                .retrieve()
                .body(CountResponse.class);

        return response == null ? 0 : response.count();
    }

    private record CountResponse(long count) {
    }
}
