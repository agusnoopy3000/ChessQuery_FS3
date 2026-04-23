CREATE TABLE game_record (
    game_id         BIGINT       PRIMARY KEY,
    white_player_id BIGINT       NOT NULL,
    black_player_id BIGINT       NOT NULL,
    result          VARCHAR(10)  NOT NULL CHECK (result IN ('1-0', '0-1', '1/2-1/2')),
    opening_id      INTEGER,
    played_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gr_white   ON game_record (white_player_id);
CREATE INDEX idx_gr_black   ON game_record (black_player_id);
CREATE INDEX idx_gr_opening ON game_record (opening_id);
