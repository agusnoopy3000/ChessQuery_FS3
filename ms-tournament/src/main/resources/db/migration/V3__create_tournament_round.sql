-- =============================================================================
-- MS-Tournament — V3: Tabla TOURNAMENT_ROUND
-- =============================================================================

CREATE TABLE tournament_round (
    id             BIGSERIAL    PRIMARY KEY,
    tournament_id  BIGINT       NOT NULL REFERENCES tournament (id) ON DELETE CASCADE,
    round_number   INTEGER      NOT NULL,
    round_date     DATE,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                                CHECK (status IN ('PENDING', 'IN_PROGRESS', 'FINISHED')),

    CONSTRAINT uq_round_tournament_number UNIQUE (tournament_id, round_number)
);

CREATE INDEX idx_round_tournament ON tournament_round (tournament_id);
