-- =============================================================================
-- MS-Game — V5: vincular partidas en vivo con emparejamientos de torneo
--
-- Cuando ms-tournament genera una ronda, crea (vía POST /games/live) una
-- partida en vivo por cada emparejamiento, pasando el id del pairing. Al
-- finalizar la partida, el evento game.finished incluye este id para que
-- ms-tournament registre el resultado automáticamente en el pairing.
-- =============================================================================

ALTER TABLE live_game_session
    ADD COLUMN tournament_pairing_id BIGINT;

CREATE INDEX idx_lgs_tournament_pairing
    ON live_game_session (tournament_pairing_id);
