DROP INDEX IF EXISTS pickup_schedules_venue_date_idx;

UPDATE pickup_schedules
SET start_date = date
WHERE start_date IS NULL
  AND date IS NOT NULL;

ALTER TABLE pickup_schedules
    DROP CONSTRAINT IF EXISTS pickup_schedules_valid_once_fields,
    DROP CONSTRAINT IF EXISTS pickup_schedules_valid_weekly_fields;

ALTER TABLE pickup_schedules
    ALTER COLUMN start_date SET NOT NULL;

ALTER TABLE pickup_schedules
    ADD CONSTRAINT pickup_schedules_valid_weekly_fields
        CHECK (recurrence_type <> 'WEEKLY' OR day_of_week IS NOT NULL);

ALTER TABLE pickup_schedules
    DROP COLUMN IF EXISTS date;
