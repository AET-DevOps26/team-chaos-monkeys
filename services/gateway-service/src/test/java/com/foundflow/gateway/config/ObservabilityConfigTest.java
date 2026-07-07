package com.foundflow.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

class ObservabilityConfigTest {

    private final ObservationPredicate predicate =
            new ObservabilityConfig().actuatorObservationPredicate();

    private static ServerRequestObservationContext requestTo(String path) {
        return new ServerRequestObservationContext(
                MockServerHttpRequest.get(path).build(), new MockServerHttpResponse(), null);
    }

    @Test
    void dropsActuatorRequests() {
        assertThat(predicate.test("http.server.requests", requestTo("/actuator/health"))).isFalse();
        assertThat(predicate.test("http.server.requests", requestTo("/actuator/prometheus"))).isFalse();
    }

    @Test
    void keepsProxiedApiRequests() {
        assertThat(predicate.test("http.server.requests", requestTo("/api/lost-items"))).isTrue();
    }

    @Test
    void keepsNonHttpObservations() {
        assertThat(predicate.test("spring.gateway.request", new Observation.Context())).isTrue();
    }
}
