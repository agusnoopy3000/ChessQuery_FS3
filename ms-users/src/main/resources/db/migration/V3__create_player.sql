-- =============================================================================
-- MS-Users — V3: Tabla PLAYER
-- Requiere extensión pg_trgm para búsqueda fuzzy de nombres.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE player (
    id                 BIGSERIAL    PRIMARY KEY,
    first_name         VARCHAR(100) NOT NULL,
    last_name          VARCHAR(100) NOT NULL,
    -- RUT chileno "12345678-9". Nullable para jugadores extranjeros.
    rut                VARCHAR(12)  UNIQUE,
    email              VARCHAR(255) UNIQUE,
    country_id         INTEGER      REFERENCES country (id),
    region             VARCHAR(100),
    club_id            INTEGER      REFERENCES club (id),
    birth_date         DATE,
    gender             CHAR(1)      CHECK (gender IN ('M', 'F', 'O')),
    fide_id            VARCHAR(20)  UNIQUE,
    federation_id      VARCHAR(50)  UNIQUE,
    lichess_username   VARCHAR(100) UNIQUE,
    -- ELO snapshots (desnormalización documentada — historial en rating_history)
    elo_national       INTEGER,
    elo_fide_standard  INTEGER,
    elo_fide_rapid     INTEGER,
    elo_fide_blitz     INTEGER,
    elo_platform       INTEGER,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ
);

-- Índice GIN para búsqueda fuzzy sobre nombre completo (pg_trgm)
CREATE INDEX idx_player_name_trgm
    ON player USING gin ((first_name || ' ' || last_name) gin_trgm_ops);

-- Índices para búsquedas exactas y ordenamiento de ranking
CREATE INDEX idx_player_email         ON player (email);
CREATE INDEX idx_player_rut           ON player (rut);
CREATE INDEX idx_player_fide_id       ON player (fide_id);
CREATE INDEX idx_player_elo_national  ON player (elo_national DESC NULLS LAST);
CREATE INDEX idx_player_birth_date    ON player (birth_date);
CREATE INDEX idx_player_region        ON player (LOWER(region));
