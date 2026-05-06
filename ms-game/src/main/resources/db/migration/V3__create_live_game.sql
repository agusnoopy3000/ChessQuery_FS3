-- =============================================================================
-- MS-Game — V3: Partidas en vivo (live_game_session + live_game_move)
--
-- Estas tablas modelan partidas activas en tiempo real entre 2 jugadores.
-- Al finalizar (resign/checkmate/draw/abandon), se construye un PGN y se
-- materializa una fila en la tabla `game` ya existente vía el flujo
-- estándar de registro. La sesión queda marcada como FINISHED.
-- =============================================================================

CREATE TABLE live_game_session (
    id                BIGSERIAL    PRIMARY KEY,
    white_player_id   BIGINT       NOT NULL,
    black_player_id   BIGINT,                        -- NULL hasta que el rival entra
    status            VARCHAR(20)  NOT NULL DEFAULT 'WAITING'
                      CHECK (status IN ('WAITING', 'ACTIVE', 'FINISHED', 'ABANDONED')),
    initial_fen       VARCHAR(120) NOT NULL DEFAULT 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1',
    current_fen       VARCHAR(120) NOT NULL,
    turn              CHAR(1)      NOT NULL DEFAULT 'w' CHECK (turn IN ('w', 'b')),
    result            VARCHAR(10)  CHECK (result IN ('1-0', '0-1', '1/2-1/2')),
    end_reason        VARCHAR(30),                    -- CHECKMATE, RESIGN, DRAW_AGREED, STALEMATE, ABANDON
    -- Reservado para reloj (V1 sin time control): se persisten para rolling
    time_control_initial_ms BIGINT,
    time_control_increment_ms BIGINT,
    clock_white_ms    BIGINT,
    clock_black_ms    BIGINT,
    -- ELO snapshot al crear (para no resolver de nuevo en finalize)
    white_elo_before  INTEGER,
    black_elo_before  INTEGER,
    -- Game id final tras materializar en tabla `game`
    finalized_game_id BIGINT,
    started_at        TIMESTAMPTZ,
    finished_at       TIMESTAMPTZ,
    last_move_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_distinct_players CHECK (black_player_id IS NULL OR black_player_id <> white_player_id)
);

CREATE INDEX idx_lgs_status     ON live_game_session (status);
CREATE INDEX idx_lgs_white      ON live_game_session (white_player_id);
CREATE INDEX idx_lgs_black      ON live_game_session (black_player_id);
CREATE INDEX idx_lgs_created_at ON live_game_session (created_at DESC);

CREATE TABLE live_game_move (
    id              BIGSERIAL    PRIMARY KEY,
    session_id      BIGINT       NOT NULL REFERENCES live_game_session (id) ON DELETE CASCADE,
    move_number     INTEGER      NOT NULL,
    player_id       BIGINT       NOT NULL,
    color           CHAR(1)      NOT NULL CHECK (color IN ('w', 'b')),
    uci             VARCHAR(10)  NOT NULL,           -- e.g. e2e4, e7e8q
    san             VARCHAR(15)  NOT NULL,           -- e.g. e4, Nf3, O-O, Qxe4#
    fen_after       VARCHAR(120) NOT NULL,
    clock_white_ms  BIGINT,
    clock_black_ms  BIGINT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_session_move UNIQUE (session_id, move_number, color)
);

CREATE INDEX idx_lgm_session ON live_game_move (session_id, move_number);

COMMENT ON TABLE live_game_session IS 'Partidas en vivo entre 2 jugadores. Se materializan en game al finalizar.';
COMMENT ON TABLE live_game_move IS 'Cada jugada individual de una partida viva, en UCI y SAN, con FEN resultante.';
