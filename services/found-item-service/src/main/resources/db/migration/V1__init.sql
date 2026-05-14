CREATE TABLE found_items (
    id UUID PRIMARY KEY,
    photo_key VARCHAR(255),
    description TEXT,
    found_at TIMESTAMP,
    location_hint VARCHAR(255),
    status VARCHAR(255),
    venue_id UUID,
    reporter_id UUID,
    category VARCHAR(255),
    brand VARCHAR(255),
    color VARCHAR(255)
);

CREATE TABLE found_item_marks (
    found_item_id UUID NOT NULL,
    marks VARCHAR(255),
    CONSTRAINT fk_found_item_marks_found_item
        FOREIGN KEY (found_item_id)
        REFERENCES found_items (id)
        ON DELETE CASCADE
);