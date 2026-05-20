ALTER TABLE notifications
ADD COLUMN venue_id UUID;

CREATE INDEX notifications_venue_id_idx ON notifications (venue_id);
