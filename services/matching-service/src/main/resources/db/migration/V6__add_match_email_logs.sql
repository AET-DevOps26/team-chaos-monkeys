CREATE TABLE match_email_logs (
    id UUID PRIMARY KEY,
    recipient VARCHAR(255) NOT NULL,
    venue_id UUID NOT NULL,
    match_id UUID NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body VARCHAR(2000) NOT NULL,
    magic_link VARCHAR(2000) NOT NULL,
    sent_at TIMESTAMP NOT NULL
);

CREATE INDEX match_email_logs_venue_sent_at_idx ON match_email_logs (venue_id, sent_at DESC);
CREATE INDEX match_email_logs_recipient_sent_at_idx ON match_email_logs (recipient, sent_at DESC);
