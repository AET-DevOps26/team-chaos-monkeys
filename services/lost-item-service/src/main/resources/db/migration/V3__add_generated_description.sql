-- AI-generated one-line item description (embedded ItemAttributes.description).
-- Named generated_description so it does not collide with lost_reports.description,
-- which holds the guest's free-text report.
ALTER TABLE lost_reports ADD COLUMN generated_description TEXT;
