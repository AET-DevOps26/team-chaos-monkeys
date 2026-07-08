package com.foundflow.operations.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Drops HTTP server observations for /actuator/** — kubelet probes and
 * Prometheus scrapes would otherwise dominate Tempo at 100% sampling (#330).
 */
@Configuration(proxyBeanMethods = false)
public class ObservabilityConfig {

    @Bean
    ObservationPredicate actuatorObservationPredicate() {
        return (name, context) ->
                !(context instanceof ServerRequestObservationContext server)
                        || !server.getCarrier().getRequestURI().startsWith("/actuator");
    }
}
