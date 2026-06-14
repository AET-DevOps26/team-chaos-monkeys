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
}
