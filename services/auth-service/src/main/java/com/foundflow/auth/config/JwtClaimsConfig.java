package com.foundflow.auth.config;

import com.foundflow.auth.domain.User;
import com.foundflow.auth.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class JwtClaimsConfig {

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer(
            UserRepository userRepository
    ) {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }

            context.getClaims().claims(claims -> {
                Set<String> roles = AuthorityUtils
                        .authorityListToSet(context.getPrincipal().getAuthorities())
                        .stream()
                        .map(authority -> authority.replaceFirst("^ROLE_", ""))
                        .collect(Collectors.collectingAndThen(
                                Collectors.toSet(),
                                Collections::unmodifiableSet
                        ));

                claims.put("roles", roles);

                String email = context.getPrincipal().getName();

                User user = userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new IllegalStateException("Authenticated user not found: " + email)
                        );

                if (user.getVenueId() != null) {
                    claims.put("venue_id", user.getVenueId().toString());
                }
            });
        };
    }
}