CREATE TABLE processed_event (
    event_id     VARCHAR(36)  PRIMARY KEY,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
