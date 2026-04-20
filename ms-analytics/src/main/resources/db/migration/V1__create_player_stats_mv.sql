CREATE TABLE player_stats_mv (
    player_id      BIGINT          PRIMARY KEY,
    total_games    INTEGER         NOT NULL DEFAULT 0,
    wins           INTEGER         NOT NULL DEFAULT 0,
    losses         INTEGER         NOT NULL DEFAULT 0,
    draws          INTEGER         NOT NULL DEFAULT 0,
    win_rate       DECIMAL(5,2)    NOT NULL DEFAULT 0.00,
    avg_moves      DECIMAL(5,1)    NOT NULL DEFAULT 0.0,
    current_streak INTEGER         NOT NULL DEFAULT 0,
    best_elo       INTEGER         NOT NULL DEFAULT 0,
    last_refreshed TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
