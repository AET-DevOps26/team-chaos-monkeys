ALTER TABLE item_embeddings
    ADD COLUMN IF NOT EXISTS contact_email VARCHAR(255);

ALTER TABLE matches
    ADD COLUMN IF NOT EXISTS recipient_email VARCHAR(255);

CREATE INDEX IF NOT EXISTS item_embeddings_contact_email_idx ON item_embeddings (contact_email);
CREATE INDEX IF NOT EXISTS matches_recipient_email_idx ON matches (recipient_email);
