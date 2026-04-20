-- =============================================================================
-- MS-Tournament — V1: Tabla TOURNAMENT
-- =============================================================================

CREATE TABLE tournament (
    id            BIGSERIAL     PRIMARY KEY,
    name          VARCHAR(200)  NOT NULL,
    description   VARCHAR(2000),
    format        VARCHAR(20)   NOT NULL CHECK (format IN ('SWISS', 'ROUND_ROBIN', 'KNOCKOUT')),
    status        VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'
                                CHECK (status IN ('DRAFT', 'OPEN', 'IN_PROGRESS', 'FINISHED')),
    start_date    DATE,
    end_date      DATE,
    location      VARCHAR(200),
    max_players   INTEGER,
    rounds_total  INTEGER,
    organizer_id  BIGINT        NOT NULL,
    min_elo       INTEGER,
    max_elo       INTEGER,
    time_control  VARCHAR(50),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ
);

CREATE INDEX idx_tournament_status  ON tournament (status);
CREATE INDEX idx_tournament_format  ON tournament (format);
CREATE INDEX idx_tournament_organizer ON tournament (organizer_id);
