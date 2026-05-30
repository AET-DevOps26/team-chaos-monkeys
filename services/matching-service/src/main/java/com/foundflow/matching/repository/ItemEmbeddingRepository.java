package com.foundflow.matching.repository;

import com.foundflow.matching.domain.ItemEmbedding;
import com.foundflow.matching.domain.ItemType;
import com.pgvector.PGvector;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
                INSERT INTO item_embeddings (id, item_type, item_id, venue_id, category, embedding, text_source, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (item_type, item_id) DO UPDATE
                SET embedding = EXCLUDED.embedding,
                    text_source = EXCLUDED.text_source,
                    venue_id = EXCLUDED.venue_id,
                    category = EXCLUDED.category,
                    updated_at = EXCLUDED.updated_at
                """,
                embedding.id(),
                embedding.itemType().name(),
                embedding.itemId(),
                embedding.venueId(),
                embedding.category(),
                new PGvector(embedding.embedding()),
                embedding.textSource(),
                now,
                now
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
                SELECT item_id, category, text_source, embedding <=> ? AS distance
                FROM item_embeddings
                WHERE item_type = ? AND venue_id = ?
                ORDER BY embedding <=> ?
                LIMIT ?
                """,
                (rs, n) -> new SimilarItemEmbedding(
                        rs.getObject(1, UUID.class),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getFloat(4)
                ),
                query,
                oppositeType.name(),
                venueId,
                query,
                k
        );
    }
}
