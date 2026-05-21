ALTER TABLE users
ADD COLUMN venue_id UUID;

CREATE INDEX users_venue_id_idx ON users (venue_id);