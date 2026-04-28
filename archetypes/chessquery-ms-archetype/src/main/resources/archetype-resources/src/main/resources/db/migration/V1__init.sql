-- Initial schema for ${artifactId}
CREATE TABLE IF NOT EXISTS schema_marker (
    id SERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
