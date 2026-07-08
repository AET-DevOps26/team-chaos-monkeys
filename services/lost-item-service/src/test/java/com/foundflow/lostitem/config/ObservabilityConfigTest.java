package com.foundflow.lostitem.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ObservabilityConfigTest {

    private final ObservationPredicate predicate =
            new ObservabilityConfig().actuatorObservationPredicate();

    private static ServerRequestObservationContext requestTo(String path) {
        return new ServerRequestObservationContext(
                new MockHttpServletRequest("GET", path), new MockHttpServletResponse());
    }

    @Test
    void dropsActuatorRequests() {
        assertThat(predicate.test("http.server.requests", requestTo("/actuator/health"))).isFalse();
        assertThat(predicate.test("http.server.requests", requestTo("/actuator/prometheus"))).isFalse();
    }

    @Test
    void keepsApiRequests() {
        assertThat(predicate.test("http.server.requests", requestTo("/api/lost-items"))).isTrue();
    }

    @Test
    void keepsNonHttpObservations() {
        assertThat(predicate.test("spring.rabbit.listener", new Observation.Context())).isTrue();
    }
}
