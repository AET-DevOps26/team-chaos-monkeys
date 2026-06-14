package com.foundflow.matching.repository;

import com.foundflow.matching.domain.ItemEmbedding;
import com.foundflow.matching.domain.ItemType;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ItemEmbeddingRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17")
                    .asCompatibleSubstituteFor("postgres")
    );

    static JdbcTemplate jdbcTemplate;
    static ItemEmbeddingRepository repository;

    @BeforeAll
    static void migrate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .placeholders(Map.of("embedding_dim", "768"))
                .cleanDisabled(false)
                .load();
        flyway.migrate();

        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new ItemEmbeddingRepository(jdbcTemplate);
    }

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM item_embeddings");
    }

    @Test
    void upsertAndFindTextSource_roundTrip() {
        UUID itemId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        repository.upsert(new ItemEmbedding(
                UUID.randomUUID(),
                ItemType.LOST,
                itemId,
                venueId,
                "Bag",
                "guest@example.com",
                randomUnitVector(),
                "Black backpack | category: Bag"
        ));

        Optional<String> text = repository.findTextSource(ItemType.LOST, itemId);
        assertThat(text).contains("Black backpack | category: Bag");
    }

    @Test
    void upsertWithSameItemTypeAndId_replacesRow_doesNotInsertDuplicate() {
        UUID itemId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        repository.upsert(new ItemEmbedding(
                UUID.randomUUID(), ItemType.LOST, itemId, venueId, "Bag", null,
                randomUnitVector(), "first"
        ));
        repository.upsert(new ItemEmbedding(
                UUID.randomUUID(), ItemType.LOST, itemId, venueId, "Wallet", "guest@example.com",
                randomUnitVector(), "second"
        ));

        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM item_embeddings WHERE item_type = 'LOST' AND item_id = ?",
                Integer.class,
                itemId
        );
        assertThat(rowCount).isEqualTo(1);
        assertThat(repository.findTextSource(ItemType.LOST, itemId)).contains("second");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT contact_email FROM item_embeddings WHERE item_type = 'LOST' AND item_id = ?",
                String.class,
                itemId
        )).isEqualTo("guest@example.com");
    }

    @Test
    void findTopKSimilar_returnsCandidatesOrderedByCosineDistance_filteredByVenueAndOppositeType() {
        UUID venueId = UUID.randomUUID();
        UUID otherVenueId = UUID.randomUUID();
        UUID nearFoundId = UUID.randomUUID();
        UUID farFoundId = UUID.randomUUID();
        UUID wrongVenueFoundId = UUID.randomUUID();
        UUID sameTypeIgnoredId = UUID.randomUUID();

        float[] query = unitVector(1.0f, 0.0f);

        // Found item, same venue, very close to query
        repository.upsert(new ItemEmbedding(
                UUID.randomUUID(), ItemType.FOUND, nearFoundId, venueId, "Bag", null,
                unitVector(0.99f, 0.14f), "near"
        ));
        // Found item, same venue, orthogonal to query
        repository.upsert(new ItemEmbedding(
                UUID.randomUUID(), ItemType.FOUND, farFoundId, venueId, "Bag", null,
                unitVector(0.0f, 1.0f), "far"
        ));
        // Found item, DIFFERENT venue, should not appear
        repository.upsert(new ItemEmbedding(
                UUID.randomUUID(), ItemType.FOUND, wrongVenueFoundId, otherVenueId, "Bag", null,
                unitVector(1.0f, 0.0f), "wrong-venue"
        ));
        // LOST in same venue, should not appear when searching FOUND
        repository.upsert(new ItemEmbedding(
                UUID.randomUUID(), ItemType.LOST, sameTypeIgnoredId, venueId, "Bag", "lost@example.com",
                unitVector(1.0f, 0.0f), "wrong-type"
        ));

        List<SimilarItemEmbedding> results = repository.findTopKSimilar(
                ItemType.FOUND,
                venueId,
                query,
                10
        );

        assertThat(results).extracting(SimilarItemEmbedding::itemId)
                .containsExactly(nearFoundId, farFoundId);
        assertThat(results.get(0).cosineDistance()).isLessThan(results.get(1).cosineDistance());
        assertThat(results.get(0).textSource()).isEqualTo("near");
        assertThat(results.get(1).textSource()).isEqualTo("far");
    }

    private static float[] randomUnitVector() {
        return unitVector(1.0f, 0.0f);
    }

    private static float[] unitVector(float x, float y) {
        float[] v = new float[768];
        v[0] = x;
        v[1] = y;
        return v;
    }
}
