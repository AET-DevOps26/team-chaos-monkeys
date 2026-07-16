package com.foundflow.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewaySecurityTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void healthEndpoint_shouldBePubliclyAccessible() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void protectedRoute_shouldReturnUnauthorizedWithoutToken() {
        webTestClient.get()
                .uri("/api/venues")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void legacyLostReportsAlias_shouldNotBePubliclyPermitted() {
        webTestClient.post()
                .uri("/api/lost-reports")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void aggregatedSwaggerConfig_shouldBePubliclyAccessibleUnderApi() {
        // Served by the gateway itself (no downstream needed); guards the /api
        // relocation + permitAll so Swagger stays reachable through the ingress.
        webTestClient.get()
                .uri("/api/v3/api-docs/swagger-config")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void swaggerUi_shouldBePubliclyAccessibleUnderApi() {
        // springdoc redirects /api/swagger-ui.html → /api/swagger-ui/index.html;
        // a redirect (not 401) proves the path is permitted and served under /api.
        webTestClient.get()
                .uri("/api/swagger-ui.html")
                .exchange()
                .expectStatus().is3xxRedirection();
    }
}
