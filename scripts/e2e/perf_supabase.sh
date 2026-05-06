#!/usr/bin/env bash
# Benchmarks de performance (Tarea 19).
# Requiere: supabase + stack corriendo, jq, hey o ab.
set -euo pipefail

: "${SUPABASE_URL:?}"
: "${SUPABASE_ANON_KEY:?}"
: "${API_GATEWAY:=http://localhost:8080}"

command -v hey >/dev/null || { echo "Instalar 'hey' (github.com/rakyll/hey)"; exit 1; }

echo "── 1. JWT validation en API Gateway (1000 reqs) ────────────────────"
TOKEN=$(curl -fsS -X POST "$SUPABASE_URL/auth/v1/token?grant_type=password" \
  -H "apikey: $SUPABASE_ANON_KEY" -H "Content-Type: application/json" \
  -d '{"email":"perf@chessquery.test","password":"perf-pass"}' | jq -r '.access_token' \
  || { echo "Crear usuario perf@chessquery.test antes de correr"; exit 1; })

hey -n 1000 -c 50 -H "Authorization: Bearer $TOKEN" "$API_GATEWAY/actuator/health" \
  | tee /tmp/perf-jwt.txt
echo "Objetivo: p95 < 50 ms"

echo "── 2. Supabase Auth (100 logins concurrentes) ──────────────────────"
hey -n 100 -c 20 -m POST -T application/json \
  -H "apikey: $SUPABASE_ANON_KEY" \
  -d '{"email":"perf@chessquery.test","password":"perf-pass"}' \
  "$SUPABASE_URL/auth/v1/token?grant_type=password" | tee /tmp/perf-login.txt
echo "Objetivo: p95 < 500 ms"

echo "── 3. Supabase Storage (50 uploads) ────────────────────────────────"
echo "(ejecutar manualmente con un PGN real; objetivo: <2s/archivo)"

echo
echo "Resultados → /tmp/perf-*.txt — agregar a docs/PERFORMANCE.md"
