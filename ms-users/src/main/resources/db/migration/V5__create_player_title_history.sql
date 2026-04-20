-- =============================================================================
-- MS-Users — V5: Tabla PLAYER_TITLE_HISTORY
-- Soluciona el grupo repetitivo de títulos (1FN) al extraerlos de PLAYER.
-- is_current=TRUE indica el título vigente del jugador.
-- =============================================================================

CREATE TABLE player_title_history (
    id          BIGSERIAL   PRIMARY KEY,
    player_id   BIGINT      NOT NULL REFERENCES player (id) ON DELETE CASCADE,
    title       VARCHAR(10) NOT NULL
        CHECK (title IN ('GM','IM','FM','CM','WGM','WIM','WFM','WCM')),
    title_date  DATE        NOT NULL,
    is_current  BOOLEAN     NOT NULL DEFAULT TRUE,
    source      VARCHAR(50)
);

CREATE INDEX idx_title_player_current ON player_title_history (player_id, is_current);
