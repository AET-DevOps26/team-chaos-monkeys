ALTER TABLE matches
ADD COLUMN venue_id UUID;

CREATE INDEX matches_venue_id_idx ON matches (venue_id);
