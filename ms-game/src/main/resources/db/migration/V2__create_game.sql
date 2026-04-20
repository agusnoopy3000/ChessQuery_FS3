-- =============================================================================
-- MS-Game — V2: Tabla GAME
-- =============================================================================

CREATE TABLE game (
    id                    BIGSERIAL    PRIMARY KEY,
    white_player_id       BIGINT       NOT NULL,
    black_player_id       BIGINT       NOT NULL,
    result                VARCHAR(10)  NOT NULL CHECK (result IN ('1-0', '0-1', '1/2-1/2')),
    game_type             VARCHAR(20)  NOT NULL CHECK (game_type IN ('TOURNAMENT', 'CASUAL')),
    white_elo_before      INTEGER,
    black_elo_before      INTEGER,
    white_elo_after       INTEGER,
    black_elo_after       INTEGER,
    total_moves           INTEGER,
    opening_id            INTEGER      REFERENCES opening (id),
    pgn_storage_key       VARCHAR(500),
    tournament_pairing_id BIGINT,
    duration_seconds      INTEGER,
    played_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_game_white_player ON game (white_player_id);
CREATE INDEX idx_game_black_player ON game (black_player_id);
CREATE INDEX idx_game_type         ON game (game_type);
CREATE INDEX idx_game_result       ON game (result);
CREATE INDEX idx_game_played_at    ON game (played_at DESC);
CREATE INDEX idx_game_pairing      ON game (tournament_pairing_id);
CREATE INDEX idx_game_opening      ON game (opening_id);
