-- =============================================================================
-- MS-Users — V4: Tabla RATING_HISTORY
-- delta y rating_prev_value son desnormalizaciones documentadas para
-- consultas analíticas de variación sin self-JOIN.
-- =============================================================================

CREATE TABLE rating_history (
    id                 BIGSERIAL   PRIMARY KEY,
    player_id          BIGINT      NOT NULL REFERENCES player (id) ON DELETE CASCADE,
    rating_type        VARCHAR(30) NOT NULL
        CHECK (rating_type IN ('NATIONAL','FIDE_STANDARD','FIDE_RAPID','FIDE_BLITZ','PLATFORM')),
    rating_value       INTEGER     NOT NULL,
    rating_prev_value  INTEGER,
    delta              SMALLINT,
    recorded_at        TIMESTAMPTZ NOT NULL,
    source             VARCHAR(50)
);

CREATE INDEX idx_rating_history_player   ON rating_history (player_id, rating_type, recorded_at DESC);
CREATE INDEX idx_rating_history_recorded ON rating_history (recorded_at DESC);
