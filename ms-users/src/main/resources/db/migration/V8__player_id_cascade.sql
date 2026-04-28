-- =============================================================================
-- MS-Users — V8: ON UPDATE CASCADE en FKs hacia player.id
--
-- Necesario para que PlayerService.syncFromAuth pueda "reclamar" un Player
-- federado (scrapeado desde AJEFECH/Lichess sin email) cuando un usuario
-- real se registra con un nombre coincidente: la operación re-asigna el
-- player.id al auth_user.id sin orfanar las filas en rating_history y
-- player_title_history.
-- =============================================================================

-- rating_history.player_id
ALTER TABLE rating_history
    DROP CONSTRAINT rating_history_player_id_fkey;

ALTER TABLE rating_history
    ADD CONSTRAINT rating_history_player_id_fkey
    FOREIGN KEY (player_id) REFERENCES player (id)
    ON UPDATE CASCADE ON DELETE CASCADE;

-- player_title_history.player_id
ALTER TABLE player_title_history
    DROP CONSTRAINT player_title_history_player_id_fkey;

ALTER TABLE player_title_history
    ADD CONSTRAINT player_title_history_player_id_fkey
    FOREIGN KEY (player_id) REFERENCES player (id)
    ON UPDATE CASCADE ON DELETE CASCADE;
