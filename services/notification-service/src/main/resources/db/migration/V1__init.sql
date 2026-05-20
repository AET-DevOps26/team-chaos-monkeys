CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL,
    recipient_address VARCHAR(255) NOT NULL,
    language VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    header VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    sent_at TIMESTAMP
);