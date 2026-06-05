-- Ratings de Lichess por modalidad, poblados por ms-etl (rating.updated source=LICHESS).
-- Aditiva: no toca datos existentes. El match se hace por lichess_username.
ALTER TABLE player ADD COLUMN IF NOT EXISTS elo_lichess_bullet    INT;
ALTER TABLE player ADD COLUMN IF NOT EXISTS elo_lichess_blitz     INT;
ALTER TABLE player ADD COLUMN IF NOT EXISTS elo_lichess_rapid     INT;
ALTER TABLE player ADD COLUMN IF NOT EXISTS elo_lichess_classical INT;
