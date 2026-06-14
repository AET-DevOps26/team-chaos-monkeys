package com.foundflow.matching.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class EmbeddingDimPlaceholderIT {

    @Container
    static PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg17")
                    .asCompatibleSubstituteFor("postgres"));

    private void migrate(int dim) {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .placeholders(Map.of("embedding_dim", String.valueOf(dim)))
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
    }

    @AfterEach
    void wipe() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).cleanDisabled(false).load().clean();
    }

    private int columnTypmod(JdbcTemplate jdbc) {
        // pgvector stores the dimension in atttypmod for the embedding column
        return jdbc.queryForObject(
                "SELECT atttypmod FROM pg_attribute " +
                "WHERE attrelid = 'item_embeddings'::regclass AND attname = 'embedding'",
                Integer.class);
    }

    @Test
    void v4_templates_to_768() {
        migrate(768);
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        assertThat(columnTypmod(jdbc)).isEqualTo(768);
    }

    @Test
    void v4_templates_to_1536() {
        migrate(1536);
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        assertThat(columnTypmod(jdbc)).isEqualTo(1536);
    }
}
