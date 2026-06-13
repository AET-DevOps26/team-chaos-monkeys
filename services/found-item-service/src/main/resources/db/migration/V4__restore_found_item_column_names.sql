DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'found_items'
          AND column_name = 'intake_text'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'found_items'
          AND column_name = 'description'
    ) THEN
        ALTER TABLE found_items RENAME COLUMN intake_text TO description;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'found_items'
          AND column_name = 'location'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'found_items'
          AND column_name = 'location_hint'
    ) THEN
        ALTER TABLE found_items RENAME COLUMN location TO location_hint;
    END IF;
END $$;
