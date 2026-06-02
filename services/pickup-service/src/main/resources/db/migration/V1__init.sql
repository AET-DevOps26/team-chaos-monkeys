CREATE TABLE pickups (
    id UUID PRIMARY KEY,
    pickup_at TIMESTAMP NOT NULL,
    venue_id UUID NOT NULL,
    match_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL
);

CREATE INDEX pickups_venue_id_idx ON pickups (venue_id);
CREATE INDEX pickups_match_id_idx ON pickups (match_id);
CREATE UNIQUE INDEX pickups_venue_pickup_at_idx ON pickups (venue_id, pickup_at);

CREATE TABLE pickup_schedules (
    id UUID PRIMARY KEY,
    date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    slot_length_minutes INTEGER NOT NULL,
    venue_id UUID NOT NULL,
    CONSTRAINT pickup_schedules_valid_time CHECK (start_time < end_time),
    CONSTRAINT pickup_schedules_valid_slot_length CHECK (slot_length_minutes > 0)
);

CREATE INDEX pickup_schedules_venue_date_idx ON pickup_schedules (venue_id, date, start_time);

CREATE TABLE pickup_email_logs (
    id UUID PRIMARY KEY,
    recipient VARCHAR(255) NOT NULL,
    venue_id UUID NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body VARCHAR(2000) NOT NULL,
    magic_link VARCHAR(2000) NOT NULL,
    sent_at TIMESTAMP NOT NULL
);

CREATE INDEX pickup_email_logs_venue_sent_at_idx ON pickup_email_logs (venue_id, sent_at DESC);
CREATE INDEX pickup_email_logs_recipient_sent_at_idx ON pickup_email_logs (recipient, sent_at DESC);
