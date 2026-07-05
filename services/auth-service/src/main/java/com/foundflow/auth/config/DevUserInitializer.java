package com.foundflow.auth.config;

import java.util.UUID;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.foundflow.auth.domain.Role;
import com.foundflow.auth.domain.User;
import com.foundflow.auth.repository.UserRepository;

@Configuration
@Profile("dev")
public class DevUserInitializer {

    @Bean
    public CommandLineRunner createDevUsers(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            seedUser(userRepository, passwordEncoder,
                    System.getenv("DEV_ADMIN_EMAIL"),
                    System.getenv("DEV_ADMIN_PASSWORD"),
                    Role.ADMIN,
                    null);

            // Demo staff account from the README reviewer walkthrough; defaults match
            // scripts/seed/seed-demo.sh so every deployment accepts the same login.
            seedUser(userRepository, passwordEncoder,
                    env("DEMO_STAFF_EMAIL", "staff.demo@foundflow.local"),
                    env("DEMO_STAFF_PASSWORD", "test12345"),
                    Role.STAFF,
                    UUID.fromString(env("DEMO_VENUE_ID", "00000000-0000-0000-0000-000000000001")));
        };
    }

    private static void seedUser(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            String email,
            String rawPassword,
            Role role,
            UUID venueId
    ) {
        if (email == null || email.isBlank()
                || rawPassword == null || rawPassword.isBlank()) {
            return;
        }

        if (userRepository.findByEmail(email).isEmpty()) {
            userRepository.save(new User(
                    email,
                    role,
                    passwordEncoder.encode(rawPassword),
                    venueId
            ));
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
