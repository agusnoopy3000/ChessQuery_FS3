-- =============================================================================
-- MS-Tournament — V2: Tabla TOURNAMENT_REGISTRATION
-- =============================================================================

CREATE TABLE tournament_registration (
    id             BIGSERIAL    PRIMARY KEY,
    tournament_id  BIGINT       NOT NULL REFERENCES tournament (id) ON DELETE CASCADE,
    player_id      BIGINT       NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'CONFIRMED'
                                CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    registered_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    seed_rating    INTEGER,

    CONSTRAINT uq_registration_tournament_player UNIQUE (tournament_id, player_id)
);

CREATE INDEX idx_registration_tournament ON tournament_registration (tournament_id);
CREATE INDEX idx_registration_player     ON tournament_registration (player_id);
CREATE INDEX idx_registration_status     ON tournament_registration (tournament_id, status);
