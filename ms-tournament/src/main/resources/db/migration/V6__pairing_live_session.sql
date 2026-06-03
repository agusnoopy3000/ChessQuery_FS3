-- =============================================================================
-- MS-Tournament — V6: vincular emparejamiento con su partida en vivo
--
-- Al generar una ronda, por cada emparejamiento se crea (vía ms-game) una
-- partida en vivo. Guardamos su id acá para que el organizador pueda observarla
-- en tiempo real y para correlacionar el resultado cuando la partida termina.
-- =============================================================================

ALTER TABLE tournament_pairing
    ADD COLUMN live_session_id BIGINT;
