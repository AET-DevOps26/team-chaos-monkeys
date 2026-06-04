package com.foundflow.matching.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class MatchVerifyMigrationIT {

    @Container
    static PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg17")
                    .asCompatibleSubstituteFor("postgres"));

    static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .placeholders(Map.of("embedding_dim", "768"))
                .load();
        flyway.migrate();

        jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM matches");
    }

    @Test
    void verifyColumnsExistOnMatchTable() {
        java.util.List<String> verifyColumns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns " +
                "WHERE table_name = 'matches' AND column_name LIKE 'verify_%'",
                String.class);
        assertThat(verifyColumns).containsExactlyInAnyOrder(
                "verify_verdict", "verify_confidence", "verify_rationale",
                "verify_model_provider", "verify_model_name", "verify_completed_at");
    }

    @Test
    void verifyVerdictCheckConstraintRejectsInvalidValues() {
        UUID matchId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO matches (id, found_item_id, lost_report_id, venue_id, status,
                                     attribute_score, semantic_score, combined_score, created_at)
                VALUES (?, ?, ?, ?, 'PENDING', 1.0, 0.9, 0.9, NOW())
                """,
                matchId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID());

        assertThatThrownBy(() ->
                jdbc.update("UPDATE matches SET verify_verdict = ? WHERE id = ?", "bogus", matchId))
                .hasMessageContaining("match_verify_verdict_chk");
    }
}
