package com.foundflow.matching.repository;

import com.foundflow.matching.domain.ItemEmbedding;
import com.foundflow.matching.domain.ItemType;
import com.pgvector.PGvector;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for the pgvector-backed {@code item_embeddings} table, and the only repository wired
 * directly on {@link JdbcTemplate}. Elsewhere the codebase uses Spring Data JPA repositories — though
 * those aren't pure ORM either (e.g. {@code MatchRepository} drops to {@code @Query(nativeQuery=true)}
 * for reporting queries). What's specific here is binding {@link com.pgvector.PGvector} parameters and
 * the {@code vector(N)} column / {@code <=>} cosine-distance operator, which our Hibernate/JPA setup
 * doesn't map; {@code JdbcTemplate} is the pragmatic fit. This predates the search endpoint —
 * {@code findTopKForSearch} just follows the existing {@code findTopKSimilar}.
 */
@Repository
public class ItemEmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;

    public ItemEmbeddingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<String> findTextSource(ItemType itemType, UUID itemId) {
        List<String> rows = jdbcTemplate.query(
                "SELECT text_source FROM item_embeddings WHERE item_type = ? AND item_id = ?",
                (rs, n) -> rs.getString(1),
                itemType.name(),
                itemId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void upsert(ItemEmbedding embedding) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                INSERT INTO item_embeddings (id, item_type, item_id, venue_id, category, contact_email, embedding, text_source, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (item_type, item_id) DO UPDATE
                SET embedding = EXCLUDED.embedding,
                    text_source = EXCLUDED.text_source,
                    venue_id = EXCLUDED.venue_id,
                    category = EXCLUDED.category,
                    contact_email = EXCLUDED.contact_email,
                    updated_at = EXCLUDED.updated_at
                """,
                embedding.id(),
                embedding.itemType().name(),
                embedding.itemId(),
                embedding.venueId(),
                embedding.category(),
                normalizeEmail(embedding.contactEmail()),
                new PGvector(embedding.embedding()),
                embedding.textSource(),
                now,
                now
        );
    }

    public int deleteByItemTypeAndItemId(ItemType itemType, UUID itemId) {
        return jdbcTemplate.update(
                "DELETE FROM item_embeddings WHERE item_type = ? AND item_id = ?",
                itemType.name(),
                itemId
        );
    }

    public List<SimilarItemEmbedding> findTopKSimilar(
            ItemType oppositeType,
            UUID venueId,
            float[] embedding,
            int k
    ) {
        PGvector query = new PGvector(embedding);
        return jdbcTemplate.query(
                """
                SELECT item_id, category, contact_email, text_source, embedding <=> ? AS distance
                FROM item_embeddings
                WHERE item_type = ? AND venue_id = ?
                ORDER BY embedding <=> ?
                LIMIT ?
                """,
                (rs, n) -> new SimilarItemEmbedding(
                        rs.getObject(1, UUID.class),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getFloat(5)
                ),
                query,
                oppositeType.name(),
                venueId,
                query,
                k
        );
    }

    /**
     * KNN over the item corpus for a free-text staff search, always scoped to a single venue.
     *
     * @param itemTypeFilter when non-null, restrict to that item type; when {@code null}, search
     *                       across both lost reports and found items.
     */
    public List<ScopedSimilarItem> findTopKForSearch(
            ItemType itemTypeFilter,
            UUID venueId,
            float[] embedding,
            int k
    ) {
        PGvector query = new PGvector(embedding);

        StringBuilder sql = new StringBuilder("""
                SELECT item_id, item_type, category, text_source, embedding <=> ? AS distance
                FROM item_embeddings
                WHERE venue_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(query);
        args.add(venueId);

        if (itemTypeFilter != null) {
            sql.append("AND item_type = ?\n");
            args.add(itemTypeFilter.name());
        }

        sql.append("""
                ORDER BY embedding <=> ?
                LIMIT ?
                """);
        args.add(query);
        args.add(k);

        return jdbcTemplate.query(
                sql.toString(),
                (rs, n) -> new ScopedSimilarItem(
                        rs.getObject(1, UUID.class),
                        ItemType.valueOf(rs.getString(2)),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getFloat(5)
                ),
                args.toArray()
        );
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase();
    }
}
