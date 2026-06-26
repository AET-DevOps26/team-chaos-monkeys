-- AI-generated one-line item description (embedded ItemAttributes.description).
-- Named generated_description to avoid colliding with the historical
-- found_items.description column, which V3 renamed to intake_text.
ALTER TABLE found_items ADD COLUMN generated_description TEXT;
