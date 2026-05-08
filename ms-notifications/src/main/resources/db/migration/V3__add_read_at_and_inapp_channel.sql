-- N1: inbox de notificaciones in-app con tracking de lectura.
-- Añadimos canal IN_APP (campana de la UI) y columna read_at para el badge
-- de no leídas.

ALTER TABLE notification_log
    DROP CONSTRAINT IF EXISTS notification_log_channel_check;

ALTER TABLE notification_log
    ADD CONSTRAINT notification_log_channel_check
    CHECK (channel IN ('EMAIL', 'PUSH', 'IN_APP'));

ALTER TABLE notification_log
    ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_notif_recipient_unread
    ON notification_log (recipient_id, read_at)
    WHERE read_at IS NULL;
