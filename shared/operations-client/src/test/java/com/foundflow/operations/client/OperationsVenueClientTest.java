package com.foundflow.operations.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Spring-free slice wiring a real {@link OperationsVenueClient} over WireMock.
 * SimpleClientHttpRequestFactory (HTTP/1.1) mirrors the production setup built
 * by {@link OperationsClientAutoConfiguration}; the JDK HttpClient default
 * negotiates HTTP/2, which is flaky against WireMock.
 */
class OperationsVenueClientTest {

    private static final UUID KNOWN_VENUE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ALL_ZEROS_PLACEHOLDER = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private WireMockServer wm;
    private OperationsVenueClient client;

    @BeforeEach
    void setUp() {
        wm = new WireMockServer(options().dynamicPort());
        wm.start();
        client = new OperationsVenueClient(RestClient.builder()
                .baseUrl(wm.baseUrl())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build());
    }

    @AfterEach
    void tearDown() {
        wm.stop();
    }

    private void stubPublicVenues() {
        wm.stubFor(get(urlEqualTo("/api/venues/public")).willReturn(
                okJson("[{\"venueId\":\"" + KNOWN_VENUE + "\",\"name\":\"Grand Plaza\"}]")));
    }

    @Test
    void requireExisting_passesForKnownVenue() {
        stubPublicVenues();

        assertDoesNotThrow(() -> client.requireExisting(KNOWN_VENUE));
    }

    @Test
    void requireExisting_rejectsUnknownVenueWith400() {
        stubPublicVenues();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> client.requireExisting(ALL_ZEROS_PLACEHOLDER)
        );
        assertEquals(400, exception.getStatusCode().value());
    }

    @Test
    void requireExisting_rejectsNullVenueWith400() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> client.requireExisting(null)
        );
        assertEquals(400, exception.getStatusCode().value());
    }

    @Test
    void requireExisting_mapsUpstreamFailureTo502() {
        wm.stubFor(get(urlEqualTo("/api/venues/public")).willReturn(aResponse().withStatus(500)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> client.requireExisting(KNOWN_VENUE)
        );
        assertEquals(502, exception.getStatusCode().value());
    }
}
