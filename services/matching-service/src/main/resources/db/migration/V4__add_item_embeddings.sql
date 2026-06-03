CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE item_embeddings (
    id UUID PRIMARY KEY,
    item_type VARCHAR(16) NOT NULL,
    item_id UUID NOT NULL,
    venue_id UUID NOT NULL,
    category VARCHAR(255),
    embedding vector(${embedding_dim}) NOT NULL,
    text_source TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT item_embeddings_type_id_unique UNIQUE (item_type, item_id)
);

CREATE INDEX item_embeddings_venue_id_idx ON item_embeddings (venue_id);

CREATE INDEX item_embeddings_embedding_idx
    ON item_embeddings
    USING hnsw (embedding vector_cosine_ops);
