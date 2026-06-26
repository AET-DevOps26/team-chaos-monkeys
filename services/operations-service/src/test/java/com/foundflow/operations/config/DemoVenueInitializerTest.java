package com.foundflow.operations.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DemoVenueInitializerTest {

    private final DemoVenueInitializer initializer = new DemoVenueInitializer();

    @Test
    void insertsDemoVenueIdempotentlyWhenEnabled() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        initializer.seedDemoVenue(jdbcTemplate, true).run();

        verify(jdbcTemplate).update(
                contains("ON CONFLICT (id) DO NOTHING"),
                eq(DemoVenueInitializer.DEMO_VENUE_ID),
                eq("Grand Plaza Hotel (Demo)"),
                eq("en"));
    }

    @Test
    void doesNothingWhenDisabled() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        initializer.seedDemoVenue(jdbcTemplate, false).run();

        verifyNoInteractions(jdbcTemplate);
    }
}
