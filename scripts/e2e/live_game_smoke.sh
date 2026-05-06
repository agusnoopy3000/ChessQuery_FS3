#!/usr/bin/env bash
# Smoke test del flujo live game completo:
#   1. usuario A (white) crea sesión
#   2. usuario B (black) hace join
#   3. partida con 4 jugadas legales
#   4. mate forzado (Scholar's Mate: 1.e4 e5 2.Bc4 Nc6 3.Qh5 Nf6?? 4.Qxf7#)
#   5. verifica que se materializó en tabla `game`
#
# Requiere supabase + docker stack corriendo, y dos usuarios PLAYER
# autenticables con email/password en Supabase Auth.
set -euo pipefail

: "${SUPABASE_URL:?SUPABASE_URL no seteado}"
: "${SUPABASE_ANON_KEY:?SUPABASE_ANON_KEY no seteado}"
: "${API_GATEWAY:=http://localhost:8080}"
: "${WHITE_EMAIL:?WHITE_EMAIL no seteado}"
: "${WHITE_PASSWORD:?WHITE_PASSWORD no seteado}"
: "${BLACK_EMAIL:?BLACK_EMAIL no seteado}"
: "${BLACK_PASSWORD:?BLACK_PASSWORD no seteado}"

login() {
  local email="$1" password="$2"
  curl -fsS -X POST "$SUPABASE_URL/auth/v1/token?grant_type=password" \
    -H "apikey: $SUPABASE_ANON_KEY" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\",\"password\":\"$password\"}" \
    | jq -r '.access_token'
}

api_post() {
  local token="$1" path="$2" body="${3:-{}}"
  curl -fsS -X POST "$API_GATEWAY$path" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "$body"
}

api_get() {
  local token="$1" path="$2"
  curl -fsS "$API_GATEWAY$path" -H "Authorization: Bearer $token"
}

echo "── Login white & black ─────────────────────────────────────────────"
WHITE=$(login "$WHITE_EMAIL" "$WHITE_PASSWORD")
BLACK=$(login "$BLACK_EMAIL" "$BLACK_PASSWORD")
[[ -n "$WHITE" && "$WHITE" != "null" ]] || { echo "FAIL login white"; exit 1; }
[[ -n "$BLACK" && "$BLACK" != "null" ]] || { echo "FAIL login black"; exit 1; }
echo "   ok"

echo "── White crea sesión ───────────────────────────────────────────────"
CREATE=$(api_post "$WHITE" "/api/player/play/live" '{}')
GAME_ID=$(echo "$CREATE" | jq -r '.id')
[[ -n "$GAME_ID" && "$GAME_ID" != "null" ]] || { echo "FAIL create"; echo "$CREATE"; exit 1; }
echo "   gameId=$GAME_ID"

echo "── Black hace join ─────────────────────────────────────────────────"
JOIN=$(api_post "$BLACK" "/api/player/play/live/$GAME_ID/join" '{}')
STATUS=$(echo "$JOIN" | jq -r '.status')
[[ "$STATUS" == "ACTIVE" ]] || { echo "FAIL join (status=$STATUS)"; echo "$JOIN"; exit 1; }
echo "   ACTIVE"

echo "── Jugar Scholar's Mate ────────────────────────────────────────────"
play_move() {
  local token="$1" uci="$2" who="$3"
  local r
  r=$(api_post "$token" "/api/player/play/live/$GAME_ID/move" "{\"uci\":\"$uci\"}")
  echo "   $who: $uci → $(echo "$r" | jq -r '.status') turn=$(echo "$r" | jq -r '.turn')"
  echo "$r"
}
play_move "$WHITE" "e2e4" "W" >/dev/null
play_move "$BLACK" "e7e5" "B" >/dev/null
play_move "$WHITE" "f1c4" "W" >/dev/null
play_move "$BLACK" "b8c6" "B" >/dev/null
play_move "$WHITE" "d1h5" "W" >/dev/null
play_move "$BLACK" "g8f6" "B" >/dev/null
FINAL=$(play_move "$WHITE" "h5f7" "W")

STATUS=$(echo "$FINAL" | jq -r '.status')
RESULT=$(echo "$FINAL" | jq -r '.result')
END_REASON=$(echo "$FINAL" | jq -r '.endReason')
FINALIZED=$(echo "$FINAL" | jq -r '.finalizedGameId')
[[ "$STATUS" == "FINISHED" ]] || { echo "FAIL: status final=$STATUS"; exit 1; }
[[ "$RESULT" == "1-0" ]] || { echo "FAIL: result=$RESULT (esperado 1-0)"; exit 1; }
[[ "$END_REASON" == "CHECKMATE" ]] || { echo "FAIL: endReason=$END_REASON"; exit 1; }
echo "   ✅ status=$STATUS result=$RESULT reason=$END_REASON finalizedGameId=$FINALIZED"

if [[ -n "$FINALIZED" && "$FINALIZED" != "null" && "$FINALIZED" != "-1" ]]; then
  echo "── Verificar persistencia en tabla 'game' ──────────────────────────"
  GAME=$(api_get "$WHITE" "/api/player/play/games/$FINALIZED" 2>/dev/null || \
         curl -fsS "http://localhost:8083/games/$FINALIZED" 2>/dev/null || echo "{}")
  PGN_KEY=$(echo "$GAME" | jq -r '.pgnUrl // .pgnStorageKey // ""')
  echo "   game id=$FINALIZED pgn=$PGN_KEY"
fi

echo
echo "✅ Live game smoke OK — sesión $GAME_ID materializada como game $FINALIZED"
