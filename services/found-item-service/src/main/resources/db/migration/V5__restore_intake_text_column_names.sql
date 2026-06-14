DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'found_items'
          AND column_name = 'description'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'found_items'
          AND column_name = 'intake_text'
    ) THEN
        ALTER TABLE found_items RENAME COLUMN description TO intake_text;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'found_items'
          AND column_name = 'location_hint'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'found_items'
          AND column_name = 'location'
    ) THEN
        ALTER TABLE found_items RENAME COLUMN location_hint TO location;
    END IF;
END $$;
