-- =============================================================================
-- MS-Tournament — V4: Tabla TOURNAMENT_PAIRING
-- =============================================================================

CREATE TABLE tournament_pairing (
    id               BIGSERIAL    PRIMARY KEY,
    round_id         BIGINT       NOT NULL REFERENCES tournament_round (id) ON DELETE CASCADE,
    white_player_id  BIGINT       NOT NULL,
    black_player_id  BIGINT       NOT NULL,
    result           VARCHAR(10)  CHECK (result IN ('1-0', '0-1', '1/2-1/2')),
    board_number     INTEGER,

    CONSTRAINT chk_pairing_different_players CHECK (white_player_id <> black_player_id)
);

CREATE INDEX idx_pairing_round        ON tournament_pairing (round_id);
CREATE INDEX idx_pairing_white_player ON tournament_pairing (white_player_id);
CREATE INDEX idx_pairing_black_player ON tournament_pairing (black_player_id);
