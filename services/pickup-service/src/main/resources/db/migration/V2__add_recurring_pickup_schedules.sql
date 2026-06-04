ALTER TABLE pickup_schedules
    ADD COLUMN recurrence_type VARCHAR(20) NOT NULL DEFAULT 'ONCE',
    ADD COLUMN start_date DATE,
    ADD COLUMN end_date DATE,
    ADD COLUMN day_of_week VARCHAR(16);

ALTER TABLE pickup_schedules
    ALTER COLUMN date DROP NOT NULL;

ALTER TABLE pickup_schedules
    ADD CONSTRAINT pickup_schedules_valid_recurrence_type
        CHECK (recurrence_type IN ('ONCE', 'WEEKLY')),
    ADD CONSTRAINT pickup_schedules_valid_once_fields
        CHECK (recurrence_type <> 'ONCE' OR date IS NOT NULL),
    ADD CONSTRAINT pickup_schedules_valid_weekly_fields
        CHECK (recurrence_type <> 'WEEKLY' OR (start_date IS NOT NULL AND day_of_week IS NOT NULL)),
    ADD CONSTRAINT pickup_schedules_valid_weekly_end_date
        CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date);

CREATE INDEX pickup_schedules_venue_recurrence_idx
    ON pickup_schedules (venue_id, recurrence_type, start_date, day_of_week, start_time);
