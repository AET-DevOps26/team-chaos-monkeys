package com.foundflow.operations.config;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Seeds a single demo venue with a fixed id so the local stack ships with a stable
 * {@code /report/<venueId>} link and a venue to scope demo data against. Enabled only
 * when {@code SEED_DEMO_DATA=true}; the insert is idempotent, so it is safe to re-run.
 */
@Configuration
public class DemoVenueInitializer {

    static final UUID DEMO_VENUE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Bean
    public CommandLineRunner seedDemoVenue(
            JdbcTemplate jdbcTemplate,
            @Value("${SEED_DEMO_DATA:false}") boolean seedDemoData) {
        return args -> {
            if (!seedDemoData) {
                return;
            }

            jdbcTemplate.update(
                    "INSERT INTO venues (id, name, default_language) VALUES (?, ?, ?) "
                            + "ON CONFLICT (id) DO NOTHING",
                    DEMO_VENUE_ID,
                    "Grand Plaza Hotel (Demo)",
                    "en");
        };
    }
}
