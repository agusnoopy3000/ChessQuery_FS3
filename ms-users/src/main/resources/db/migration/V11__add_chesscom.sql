-- Vinculación con Chess.com + ratings por modalidad (sync desde ms-etl).
-- Aditiva: no toca datos existentes. El match se hace por chesscom_username,
-- igual que el flujo de Lichess (V3 lichess_username + V10 elo_lichess_*).
ALTER TABLE player ADD COLUMN IF NOT EXISTS chesscom_username VARCHAR(100) UNIQUE;
ALTER TABLE player ADD COLUMN IF NOT EXISTS elo_chesscom_bullet INT;
ALTER TABLE player ADD COLUMN IF NOT EXISTS elo_chesscom_blitz  INT;
ALTER TABLE player ADD COLUMN IF NOT EXISTS elo_chesscom_rapid  INT;
ALTER TABLE player ADD COLUMN IF NOT EXISTS elo_chesscom_daily  INT;
