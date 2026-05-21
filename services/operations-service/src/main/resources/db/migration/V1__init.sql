CREATE TABLE venues (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    tone VARCHAR(255),
    default_language VARCHAR(255)
);