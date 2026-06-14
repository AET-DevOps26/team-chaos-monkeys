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
                UUID.randomUUID(), ItemType.LOST, itemId, venueId, "Bag",
                randomUnitVector(), "first"
        ));
        repository.upsert(new ItemEmbedding(
                UUID.randomUUID(), ItemType.LOST, itemId, venueId, "Wallet",
                randomUnitVector(), "second"
        ));

        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM item_embeddings WHERE item_type = 'LOST' AND item_id = ?",
                Integer.class,
                itemId
        );
        assertThat(rowCount).isEqualTo(1);
        assertThat(repository.findTextSource(ItemType.LOST, itemId)).contains("second");
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
                UUID.randomUUID(), ItemType.FOUND, nearFoundId, venueId, "Bag",
                unitVector(0.99f, 0.14f), "near"
        ));
        // Found item, same venue, orthogonal to query
        repository.upsert(new ItemEmbedding(
                UUID.randomUUID(), ItemType.FOUND, farFoundId, venueId, "Bag",
                unitVector(0.0f, 1.0f), "far"
        ));
        // Found item, DIFFERENT venue, should not appear
        repository.upsert(new ItemEmbedding(
                UUID.randomUUID(), ItemType.FOUND, wrongVenueFoundId, otherVenueId, "Bag",
                unitVector(1.0f, 0.0f), "wrong-venue"
        ));
        // LOST in same venue, should not appear when searching FOUND
        repository.upsert(new ItemEmbedding(
                UUID.randomUUID(), ItemType.LOST, sameTypeIgnoredId, venueId, "Bag",
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

    @Test
    void findTopKForSearch_bothScope_returnsAcrossItemTypesOrderedByCosineDistance() {
        UUID venueId = UUID.randomUUID();
        UUID foundId = UUID.randomUUID();
        UUID lostId = UUID.randomUUID();

        float[] query = unitVector(1.0f, 0.0f);

        repository.upsert(new ItemEmbedding(
                UUID.randomUUID(), ItemType.FOUND, foundId, venueId, "Bag",
                unitVector(0.99f, 0.14f), "found-near"
        ));
        repository.upsert(new ItemEmbedding(
                UUID.randomUUID(), ItemType.LOST, lostId, venueId, "Bag",
                unitVector(0.8f, 0.6f), "lost-far"
        ));

        List<ScopedSimilarItem> results = repository.findTopKForSearch(null, venueId, query, 10);

        assertThat(results).extracting(ScopedSimilarItem::itemId)
                .containsExactly(foundId, lostId);
        assertThat(results).extracting(ScopedSimilarItem::itemType)
                .containsExactly(ItemType.FOUND, ItemType.LOST);
        assertThat(results.get(0).cosineDistance()).isLessThan(results.get(1).cosineDistance());
    }

    @Test
    void findTopKForSearch_foundScope_returnsOnlyFoundItems() {
        UUID venueId = UUID.randomUUID();
        UUID foundId = UUID.randomUUID();
        UUID lostId = UUID.randomUUID();

        float[] query = unitVector(1.0f, 0.0f);

        repository.upsert(new ItemEmbedding(
                UUID.randomUUID(), ItemType.FOUND, foundId, venueId, "Bag",
                unitVector(1.0f, 0.0f), "found"
        ));
        repository.upsert(new ItemEmbedding(
                UUID.randomUUID(), ItemType.LOST, lostId, venueId, "Bag",
                unitVector(1.0f, 0.0f), "lost"
        ));

        List<ScopedSimilarItem> results = repository.findTopKForSearch(ItemType.FOUND, venueId, query, 10);

        assertThat(results).extracting(ScopedSimilarItem::itemId).containsExactly(foundId);
        assertThat(results).allSatisfy(r -> assertThat(r.itemType()).isEqualTo(ItemType.FOUND));
    }

    @Test
    void findTopKForSearch_lostScope_returnsOnlyLostItems() {
        UUID venueId = UUID.randomUUID();
        UUID foundId = UUID.randomUUID();
        UUID lostId = UUID.randomUUID();

        float[] query = unitVector(1.0f, 0.0f);

        repository.upsert(new ItemEmbedding(
                UUID.randomUUID(), ItemType.FOUND, foundId, venueId, "Bag",
                unitVector(1.0f, 0.0f), "found"
        ));
        repository.upsert(new ItemEmbedding(
                UUID.randomUUID(), ItemType.LOST, lostId, venueId, "Bag",
                unitVector(1.0f, 0.0f), "lost"
        ));

        List<ScopedSimilarItem> results = repository.findTopKForSearch(ItemType.LOST, venueId, query, 10);

        assertThat(results).extracting(ScopedSimilarItem::itemId).containsExactly(lostId);
        assertThat(results).allSatisfy(r -> assertThat(r.itemType()).isEqualTo(ItemType.LOST));
    }

    @Test
    void findTopKForSearch_isVenueIsolated_otherVenueRowNeverSurfaces() {
        UUID venueId = UUID.randomUUID();
        UUID otherVenueId = UUID.randomUUID();
        UUID mineId = UUID.randomUUID();
        UUID othersId = UUID.randomUUID();

        float[] query = unitVector(1.0f, 0.0f);

        // My venue: a decent (not perfect) match.
        repository.upsert(new ItemEmbedding(
                UUID.randomUUID(), ItemType.FOUND, mineId, venueId, "Bag",
                unitVector(0.9f, 0.43f), "mine"
        ));
        // Other venue: a PERFECT match (identical to the query) — must still never surface.
        repository.upsert(new ItemEmbedding(
                UUID.randomUUID(), ItemType.FOUND, othersId, otherVenueId, "Bag",
                unitVector(1.0f, 0.0f), "other-venue"
        ));

        List<ScopedSimilarItem> results = repository.findTopKForSearch(null, venueId, query, 10);

        assertThat(results).extracting(ScopedSimilarItem::itemId).containsExactly(mineId);
        assertThat(results).extracting(ScopedSimilarItem::itemId).doesNotContain(othersId);
    }

    @Test
    void findTopKForSearch_respectsLimit() {
        UUID venueId = UUID.randomUUID();
        float[] query = unitVector(1.0f, 0.0f);

        repository.upsert(new ItemEmbedding(
                UUID.randomUUID(), ItemType.FOUND, UUID.randomUUID(), venueId, "Bag",
                unitVector(1.0f, 0.0f), "a"
        ));
        repository.upsert(new ItemEmbedding(
                UUID.randomUUID(), ItemType.FOUND, UUID.randomUUID(), venueId, "Bag",
                unitVector(0.9f, 0.43f), "b"
        ));
        repository.upsert(new ItemEmbedding(
                UUID.randomUUID(), ItemType.LOST, UUID.randomUUID(), venueId, "Bag",
                unitVector(0.8f, 0.6f), "c"
        ));

        List<ScopedSimilarItem> results = repository.findTopKForSearch(null, venueId, query, 2);

        assertThat(results).hasSize(2);
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
