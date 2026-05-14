CREATE TABLE matches (
    id UUID PRIMARY KEY,
    found_item_id UUID NOT NULL,
    lost_report_id UUID NOT NULL,
    attribute_score REAL NOT NULL,
    semantic_score REAL NOT NULL,
    combined_score REAL NOT NULL,
    created_at TIMESTAMP NOT NULL
);