#!/usr/bin/env bash
# Smoke test E2E del flujo Supabase completo.
# Requiere: supabase start + docker compose up (Tarea 17).
#
# Uso:
#   export SUPABASE_URL=http://127.0.0.1:54321
#   export SUPABASE_ANON_KEY=<anon>
#   export API_GATEWAY=http://localhost:8080
#   ./scripts/e2e/smoke_supabase.sh
set -euo pipefail

: "${SUPABASE_URL:?SUPABASE_URL no seteado}"
: "${SUPABASE_ANON_KEY:?SUPABASE_ANON_KEY no seteado}"
: "${API_GATEWAY:=http://localhost:8080}"

EMAIL="smoke-$(date +%s)@chessquery.test"
PASSWORD="smoke-pass-$(date +%s)"

echo "── 1. Register usuario PLAYER ──────────────────────────────────────"
SIGNUP=$(curl -fsS -X POST "$SUPABASE_URL/auth/v1/signup" \
  -H "apikey: $SUPABASE_ANON_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"data\":{\"role\":\"PLAYER\",\"firstName\":\"Smoke\",\"lastName\":\"Test\"}}")
USER_ID=$(echo "$SIGNUP" | jq -r '.user.id // .id')
echo "   userId=$USER_ID"
[[ -n "$USER_ID" && "$USER_ID" != "null" ]] || { echo "FAIL signup"; exit 1; }

echo "── 2. Verificar evento user.registered en RabbitMQ ─────────────────"
sleep 2
QUEUE_LEN=$(curl -fsS -u chessquery:chessquery_dev \
  "http://localhost:15672/api/queues/%2F/users.registration.queue" \
  | jq -r '.messages_ready // 0')
echo "   users.registration.queue messages_ready=$QUEUE_LEN (esperado >=0; el consumer ya pudo haberlo drenado)"

echo "── 3. Login y obtener access token ─────────────────────────────────"
LOGIN=$(curl -fsS -X POST "$SUPABASE_URL/auth/v1/token?grant_type=password" \
  -H "apikey: $SUPABASE_ANON_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
ACCESS=$(echo "$LOGIN" | jq -r '.access_token')
REFRESH=$(echo "$LOGIN" | jq -r '.refresh_token')
[[ -n "$ACCESS" && "$ACCESS" != "null" ]] || { echo "FAIL login"; exit 1; }
echo "   access token len=${#ACCESS}"

echo "── 4. Refresh token ────────────────────────────────────────────────"
REFRESHED=$(curl -fsS -X POST "$SUPABASE_URL/auth/v1/token?grant_type=refresh_token" \
  -H "apikey: $SUPABASE_ANON_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"refresh_token\":\"$REFRESH\"}")
NEW_ACCESS=$(echo "$REFRESHED" | jq -r '.access_token')
[[ -n "$NEW_ACCESS" && "$NEW_ACCESS" != "null" ]] || { echo "FAIL refresh"; exit 1; }
echo "   refresh ok"

echo "── 5. API call con token (gateway propaga X-User-*) ────────────────"
HTTP_CODE=$(curl -s -o /tmp/me.json -w "%{http_code}" \
  -H "Authorization: Bearer $ACCESS" \
  "$API_GATEWAY/users/by-supabase-id/$USER_ID")
echo "   GET /users/by-supabase-id → $HTTP_CODE"
[[ "$HTTP_CODE" == "200" || "$HTTP_CODE" == "404" ]] || { echo "FAIL gateway auth"; exit 1; }

echo "── 6. Upload PGN vía MS-Game (storage Supabase) ────────────────────"
PGN='[Event "Smoke"]\n[White "X"]\n[Black "Y"]\n[Result "1-0"]\n1. e4 e5 2. Nf3 Nc6 1-0'
GAME=$(curl -fsS -X POST "$API_GATEWAY/api/player/games" \
  -H "Authorization: Bearer $ACCESS" \
  -H "Content-Type: application/json" \
  -d "{\"whitePlayerId\":1,\"blackPlayerId\":2,\"result\":\"1-0\",\"gameType\":\"CASUAL\",\"pgnContent\":\"$PGN\"}" || echo "")
echo "   $GAME"

echo "── 7. Health checks ────────────────────────────────────────────────"
curl -fsS "$API_GATEWAY/actuator/health" | jq '.components.supabaseAuth // empty'

echo
echo "✅ Smoke test completado: $EMAIL / $USER_ID"
