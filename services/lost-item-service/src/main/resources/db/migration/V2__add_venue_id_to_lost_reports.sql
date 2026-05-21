ALTER TABLE lost_reports
ADD COLUMN venue_id UUID;

CREATE INDEX lost_reports_venue_id_idx ON lost_reports (venue_id);
