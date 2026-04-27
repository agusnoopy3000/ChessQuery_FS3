-- =============================================================================
-- MS-Users — V7: enriquecimiento federado (AJEFECH, Lichess, FIDE)
-- Agrega trazabilidad de la fuente de los datos del player y soporte
-- de idempotencia para los consumers de eventos rating.* / etl.*
-- =============================================================================

ALTER TABLE player
    ADD COLUMN IF NOT EXISTS enrichment_source VARCHAR(20),
    ADD COLUMN IF NOT EXISTS enriched_at       TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_player_federation_id ON player (federation_id);
CREATE INDEX IF NOT EXISTS idx_player_enrichment_source ON player (enrichment_source);

-- Tabla de eventos procesados (idempotencia). Mismo patrón que en
-- ms-notifications/V2__create_processed_event.sql.
CREATE TABLE IF NOT EXISTS processed_event (
    event_id     UUID         PRIMARY KEY,
    event_type   VARCHAR(50)  NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_processed_event_type ON processed_event (event_type);
