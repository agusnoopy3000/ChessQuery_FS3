#!/usr/bin/env bash
# =============================================================================
# demo-reset.sh — Limpia datos de demo conservando los seeds.
#
# Borra:
#   - Usuarios Supabase Auth con email *@demo.cl
#   - Players con id > 13 (los seeds chilenos ocupan ids 4..13)
#   - Sesiones live (live_game_session, live_game_move)
#   - Games registrados
#   - Torneos en estado DRAFT u OPEN, con sus pairings/rounds/registrations
#
# Conserva:
#   - 10 jugadores chilenos seed (ids 4..13)
#   - 90 aperturas ECO en tabla opening
#   - Países, clubes, ratings históricos
#
# Idempotente — se puede ejecutar varias veces sin error.
# =============================================================================

set -euo pipefail

DB_USER="${DB_USER:-chessquery}"
DB_PASSWORD="${DB_PASSWORD:-chessquery_dev}"
PG="docker exec -i -e PGPASSWORD=${DB_PASSWORD} chessquery_user_db psql"

run_psql() {
  local container="$1" db="$2" sql="$3"
  docker exec -i -e PGPASSWORD="${DB_PASSWORD}" "${container}" \
    psql -U "${DB_USER}" -d "${db}" -v ON_ERROR_STOP=1 -c "${sql}" >/dev/null
}

echo "[demo-reset] 1/5 Limpiando torneos DRAFT/OPEN..."
run_psql chessquery_tournament_db tournament_db "
  TRUNCATE tournament_pairing, tournament_round, tournament_registration RESTART IDENTITY CASCADE;
  DELETE FROM tournament WHERE status IN ('DRAFT','OPEN');
"

echo "[demo-reset] 2/5 Limpiando sesiones live + games..."
run_psql chessquery_game_db game_db "
  TRUNCATE live_game_move, live_game_session, game RESTART IDENTITY CASCADE;
"

echo "[demo-reset] 3/5 Limpiando players de demo (ids > 13)..."
run_psql chessquery_user_db user_db "
  DELETE FROM rating_history WHERE player_id > 13;
  DELETE FROM player_title_history WHERE player_id > 13;
  DELETE FROM player WHERE id > 13;
"

echo "[demo-reset] 4/5 Limpiando usuarios Supabase Auth con email *@demo.cl..."
# El postgres de Supabase corre dentro del contenedor supabase_db_*.
SUPA_DB=$(docker ps --format '{{.Names}}' | grep -m1 '^supabase_db_' || true)
if [ -n "${SUPA_DB}" ]; then
  docker exec -i -e PGPASSWORD=postgres "${SUPA_DB}" \
    psql -U postgres -d postgres -v ON_ERROR_STOP=1 \
    -c "DELETE FROM auth.users WHERE email LIKE '%@demo.cl';" >/dev/null \
    || echo "[demo-reset]   WARN: no se pudo limpiar auth.users"
else
  echo "[demo-reset]   skip: contenedor supabase_db no encontrado (¿supabase corriendo?)"
fi

echo "[demo-reset] 5/5 Limpiando notification_log..."
run_psql chessquery_notif_db notif_db "
  TRUNCATE notification_log, processed_event RESTART IDENTITY;
" || echo "[demo-reset]   skip: tablas notif aún no creadas"

echo "[demo-reset] OK — listo para una demo limpia."
