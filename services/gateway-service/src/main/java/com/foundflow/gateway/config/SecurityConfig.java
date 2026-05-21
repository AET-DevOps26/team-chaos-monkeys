package com.foundflow.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(
                                "/actuator/health",
                                "/actuator/info",
                                "/actuator/prometheus"
                        ).permitAll()
                        .pathMatchers(
                                "/auth/actuator/health",
                                "/auth/actuator/info",
                                "/auth/actuator/prometheus",
                                "/lost-items/actuator/health",
                                "/lost-items/actuator/info",
                                "/lost-items/actuator/prometheus",
                                "/found-items/actuator/health",
                                "/found-items/actuator/info",
                                "/found-items/actuator/prometheus",
                                "/matches/actuator/health",
                                "/matches/actuator/info",
                                "/matches/actuator/prometheus",
                                "/notifications/actuator/health",
                                "/notifications/actuator/info",
                                "/notifications/actuator/prometheus",
                                "/venues/actuator/health",
                                "/venues/actuator/info",
                                "/venues/actuator/prometheus"
                        ).permitAll()
                        .pathMatchers(HttpMethod.POST,
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout"
                        ).permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/lost-items", "/api/lost-reports").permitAll()
                        .pathMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .pathMatchers(
                                "/auth/v3/api-docs",
                                "/lost-items/v3/api-docs",
                                "/found-items/v3/api-docs",
                                "/matches/v3/api-docs",
                                "/notifications/v3/api-docs",
                                "/venues/v3/api-docs"
                        ).permitAll()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> {})
                )
                .build();
    }
}
