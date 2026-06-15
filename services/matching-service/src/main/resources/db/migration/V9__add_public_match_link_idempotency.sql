ALTER TABLE matches
    ADD COLUMN IF NOT EXISTS public_link_token VARCHAR(2000),
    ADD COLUMN IF NOT EXISTS public_link_recipient_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS public_link_issued_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS matches_public_link_recipient_idx
    ON matches (public_link_recipient_email);
