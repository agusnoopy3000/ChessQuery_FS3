CREATE TABLE notification_log (
    id            BIGSERIAL     PRIMARY KEY,
    recipient_id  BIGINT        NOT NULL,
    channel       VARCHAR(10)   NOT NULL CHECK (channel IN ('EMAIL', 'PUSH')),
    event_type    VARCHAR(50)   NOT NULL,
    status        VARCHAR(10)   NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    subject       VARCHAR(300),
    payload       TEXT,
    sent_at       TIMESTAMPTZ,
    retry_count   SMALLINT      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notif_recipient  ON notification_log (recipient_id);
CREATE INDEX idx_notif_status     ON notification_log (status);
CREATE INDEX idx_notif_event_type ON notification_log (event_type);
