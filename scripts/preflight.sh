#!/bin/bash
# =============================================================================
# ChessQuery — Preflight check (correr 5 min antes de la demo)
# =============================================================================
# Verifica que todo esté operativo. Sale con código 0 si OK, 1 si hay problemas.
# Uso:
#   bash scripts/preflight.sh
# =============================================================================

OK=0
FAIL=0
WARN=0

c_ok()   { echo -e "  \033[32mOK\033[0m  $1"; OK=$((OK+1)); }
c_fail() { echo -e "  \033[31mFAIL\033[0m $1"; FAIL=$((FAIL+1)); }
c_warn() { echo -e "  \033[33mWARN\033[0m $1"; WARN=$((WARN+1)); }
section(){ echo; echo -e "\033[36m── $1 ──\033[0m"; }

# ── 1. Servicios HTTP (actuator/health) ──────────────────────────────────
section "Servicios HTTP"

check_http() {
  local name="$1"
  local url="$2"
  local expect="${3:-200}"
  local code
  code=$(curl -s -o /dev/null -m 5 -w "%{http_code}" "$url" 2>/dev/null)
  if [ "$code" = "$expect" ]; then
    c_ok "$name → $url ($code)"
  else
    c_fail "$name → $url ($code, esperaba $expect)"
  fi
}

check_http "ms-users"          "http://localhost:8081/actuator/health"
check_http "ms-tournament"     "http://localhost:8082/actuator/health"
check_http "ms-game"           "http://localhost:8083/actuator/health"
check_http "ms-notifications"  "http://localhost:8085/actuator/health"
check_http "api-gateway"       "http://localhost:8080/actuator/health"
check_http "bff-player"        "http://localhost:3001/health"
check_http "bff-organizer"     "http://localhost:3002/health"

ETL_CODE=$(curl -s -o /dev/null -m 3 -w "%{http_code}" "http://localhost:8086/health" 2>/dev/null)
if [ "$ETL_CODE" = "200" ]; then
  c_ok "ms-etl → http://localhost:8086/health ($ETL_CODE)"
else
  c_warn "ms-etl apagado (OK para demo, está fuera de scope)"
fi

# ── 2. Supabase ──────────────────────────────────────────────────────────
section "Supabase"

check_http "Supabase Auth (JWKS)" "http://127.0.0.1:54321/auth/v1/.well-known/jwks.json"
check_http "Supabase Inbucket"    "http://127.0.0.1:54324"
# Studio es opcional (lo apagamos para reducir RAM en demo)
STUDIO_CODE=$(curl -s -o /dev/null -m 3 -w "%{http_code}" "http://127.0.0.1:54323" 2>/dev/null)
if [ "$STUDIO_CODE" = "200" ]; then
  c_ok "Supabase Studio activo (opcional)"
else
  c_warn "Supabase Studio apagado (OK para demo, encender solo si lo necesitas)"
fi

# Verificar storage bucket existe
SK="${SUPABASE_SERVICE_KEY:-eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImV4cCI6MTk4MzgxMjk5Nn0.EGIM96RAZx35lJzdJsyH-qQwv8Hdp7fsn3W0YpN81IU}"
BUCKETS=$(curl -s -m 5 "http://127.0.0.1:54321/storage/v1/bucket" -H "Authorization: Bearer $SK" 2>/dev/null)
if echo "$BUCKETS" | grep -q "chessquery-pgn"; then
  c_ok "Storage bucket chessquery-pgn existe"
else
  c_warn "Storage bucket chessquery-pgn no encontrado (PGN upload puede fallar)"
fi

# ── 3. RabbitMQ ──────────────────────────────────────────────────────────
section "RabbitMQ"

RMQ=$(curl -s -m 5 -u chessquery:chessquery_dev "http://localhost:15672/api/queues" 2>/dev/null)
if [ -z "$RMQ" ]; then
  c_fail "RabbitMQ Management API no responde"
else
  for Q in "notifications.game.events" "users.elo.queue" "notifications.user.events"; do
    if echo "$RMQ" | grep -q "\"$Q\""; then
      c_ok "Queue $Q existe"
    else
      c_warn "Queue $Q no encontrada"
    fi
  done
fi

# ── 4. Bases de datos ────────────────────────────────────────────────────
section "Bases de datos"

check_db() {
  local name="$1"
  local container="$2"
  local db="$3"
  if docker exec "$container" pg_isready -U chessquery -d "$db" >/dev/null 2>&1; then
    c_ok "$name (postgres ready)"
  else
    c_fail "$name no responde"
  fi
}

check_db "user_db"       chessquery_user_db        user_db
check_db "tournament_db" chessquery_tournament_db  tournament_db
check_db "game_db"       chessquery_game_db        game_db
check_db "notif_db"      chessquery_notif_db       notif_db
if docker ps --format '{{.Names}}' | grep -qx "chessquery_etl_db"; then
  check_db "etl_db"      chessquery_etl_db         etl_db
else
  c_warn "etl_db apagada (OK para demo, está fuera de scope)"
fi

# ── 5. Frontends ─────────────────────────────────────────────────────────
section "Frontends"

check_http "chess-portal"    "http://localhost:5173"
check_http "organizer-panel" "http://localhost:5174"

# ── 6. Latencia de cold-start de Supabase Storage ────────────────────────
section "Latencia Supabase Storage (warm-up)"

T=$(curl -s -o /dev/null -m 15 -w "%{time_total}" "http://127.0.0.1:54321/storage/v1/bucket/chessquery-pgn" -H "Authorization: Bearer $SK" 2>/dev/null)
TMS=$(echo "$T * 1000" | bc 2>/dev/null | cut -d. -f1)
if [ -n "$TMS" ] && [ "$TMS" -lt 1000 ]; then
  c_ok "Storage warm ($TMS ms)"
else
  c_warn "Storage cold ($TMS ms) — primera subida PGN puede tardar"
fi

# ── 7. Players seed ──────────────────────────────────────────────────────
section "Players seed"

for E in carla@demo.cl bruno@demo.cl ana@demo.cl; do
  R=$(curl -s -m 5 "http://localhost:8081/users/by-email?email=$E" 2>/dev/null)
  if echo "$R" | grep -q '"id"'; then
    NID=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])" 2>/dev/null)
    c_ok "$E provisionado (id=$NID)"
  else
    c_warn "$E no provisionado todavía (se creará en su primer login)"
  fi
done

# ── 8. Resumen ───────────────────────────────────────────────────────────
echo
echo "════════════════════════════════════════════════════════════════"
echo "  OK=$OK  WARN=$WARN  FAIL=$FAIL"
echo "════════════════════════════════════════════════════════════════"
if [ "$FAIL" -gt 0 ]; then
  echo -e "\033[31m  ❌ Hay fallos críticos. Revisa antes de la demo.\033[0m"
  echo "  Fixes rápidos:"
  echo "    - Login devuelve 504/502:  docker restart supabase_kong_ChessQuery_FS3"
  echo "    - Servicio caído:          docker compose -f infrastructure/docker-compose.yml restart <servicio>"
  echo "    - Frontends abajo:         cd frontend && npm run dev:portal (otro tab: dev:organizer)"
  exit 1
elif [ "$WARN" -gt 0 ]; then
  echo -e "\033[33m  ⚠️  Hay warnings. Demo puede correr pero revisa.\033[0m"
  exit 0
else
  echo -e "\033[32m  ✅ Todo OK. Demo lista.\033[0m"
  exit 0
fi
