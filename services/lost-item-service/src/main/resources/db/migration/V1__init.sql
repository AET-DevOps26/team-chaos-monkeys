CREATE TABLE lost_reports (
    id UUID PRIMARY KEY,
    photo_key VARCHAR(255),
    description TEXT,
    lost_at TIMESTAMP,
    location VARCHAR(255),
    status VARCHAR(255),
    contact_email VARCHAR(255),
    category VARCHAR(255),
    brand VARCHAR(255),
    color VARCHAR(255)
);

CREATE TABLE lost_report_marks (
    lost_report_id UUID NOT NULL,
    marks VARCHAR(255),
    CONSTRAINT fk_lost_report_marks_lost_report
        FOREIGN KEY (lost_report_id)
        REFERENCES lost_reports (id)
        ON DELETE CASCADE
);