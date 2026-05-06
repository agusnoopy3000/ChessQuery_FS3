-- =============================================================================
-- V9: Agregar columna supabase_user_id para mapeo UUID ↔ BIGINT
--
-- Propósito: Permite mapear el UUID de Supabase Auth (auth.users.id) con
-- el BIGINT auto-increment de PLAYER.id. Necesario durante y después de
-- la migración de MS-Auth a Supabase Auth.
--
-- Se ejecuta automáticamente por Flyway al iniciar MS-Users.
-- =============================================================================

ALTER TABLE player ADD COLUMN supabase_user_id UUID;

CREATE UNIQUE INDEX idx_player_supabase_user_id ON player(supabase_user_id);

COMMENT ON COLUMN player.supabase_user_id IS 'UUID del usuario en Supabase Auth (auth.users.id). Permite mapeo entre UUID de Supabase y BIGINT de PLAYER.id.';
