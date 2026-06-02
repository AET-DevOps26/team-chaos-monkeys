-- verify_completed_at uses TIMESTAMPTZ (vs TIMESTAMP in V1) because the Java side stores
-- OffsetDateTime — TIMESTAMPTZ preserves the offset; bare TIMESTAMP would silently drop it.
ALTER TABLE matches
    ADD COLUMN verify_verdict          VARCHAR(16),
    ADD COLUMN verify_confidence       REAL,
    ADD COLUMN verify_rationale        TEXT,
    ADD COLUMN verify_model_provider   VARCHAR(32),
    ADD COLUMN verify_model_name       VARCHAR(64),
    ADD COLUMN verify_completed_at     TIMESTAMPTZ;

ALTER TABLE matches
    ADD CONSTRAINT match_verify_verdict_chk
    CHECK (verify_verdict IS NULL OR verify_verdict IN ('match','no_match','uncertain'));
