package com.foundflow.operations.service;

import com.foundflow.operations.security.VenueAccessService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VenueKpiServiceTest {

    @Test
    void getKpis_shouldAggregateDownstreamCountsForOwnVenue() throws Exception {
        List<RecordedRequest> requests = new ArrayList<>();
        HttpServer server = startServer(requests);

        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            VenueKpiService service = new VenueKpiService(
                    new VenueAccessService(),
                    RestClient.builder(),
                    baseUrl,
                    baseUrl,
                    baseUrl
            );
            UUID venueId = UUID.randomUUID();

            var response = service.getKpis(staffJwt(venueId));

            assertEquals(venueId, response.venueId());
            assertEquals(3, response.totalFoundItems());
            assertEquals(4, response.totalLostItems());
            assertEquals(5, response.totalMatches());
            assertEquals(2, response.pendingMatches());

            assertEquals(4, requests.size());
            assertTrue(requests.stream().allMatch(request ->
                    "Bearer kpi-token".equals(request.authorization())
            ));
            assertTrue(requests.stream().allMatch(request ->
                    request.query().contains("venueId=" + venueId)
            ));
            assertTrue(requests.stream().anyMatch(request ->
                    request.path().equals("/api/matches/count")
                            && request.query().contains("status=PENDING")
            ));
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startServer(List<RecordedRequest> requests) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            requests.add(new RecordedRequest(
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getRawQuery(),
                    exchange.getRequestHeaders().getFirst("Authorization")
            ));
            writeJson(exchange, countResponse(exchange));
        });
        server.start();
        return server;
    }

    private String countResponse(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getRawQuery();

        if (path.equals("/api/found-items/count")) {
            return "{\"count\":3}";
        }

        if (path.equals("/api/lost-items/count")) {
            return "{\"count\":4}";
        }

        if (path.equals("/api/matches/count") && query != null && query.contains("status=PENDING")) {
            return "{\"count\":2}";
        }

        if (path.equals("/api/matches/count")) {
            return "{\"count\":5}";
        }

        return "{\"count\":0}";
    }

    private void writeJson(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private Jwt staffJwt(UUID venueId) {
        return Jwt.withTokenValue("kpi-token")
                .header("alg", "none")
                .claim("roles", List.of("STAFF"))
                .claim("venue_id", venueId.toString())
                .build();
    }

    private record RecordedRequest(
            String path,
            String query,
            String authorization
    ) {
    }
}
