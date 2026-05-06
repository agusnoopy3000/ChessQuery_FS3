#!/usr/bin/env bash
# Verificación de seguridad post-migración (Tarea 18).
set -o pipefail

PASS=0; FAIL=0
ok()   { echo "✅ $1"; PASS=$((PASS+1)); }
fail() { echo "❌ $1"; FAIL=$((FAIL+1)); }

echo "── 1. JWT secret tiene >=32 caracteres ─────────────────────────────"
LEN=${#SUPABASE_JWT_SECRET}
(( LEN >= 32 )) && ok "SUPABASE_JWT_SECRET length=$LEN" || fail "SUPABASE_JWT_SECRET muy corto ($LEN)"

echo "── 2. Service Key NO está en frontends ─────────────────────────────"
if grep -rq "service_role\|SUPABASE_SERVICE_KEY" frontend/apps/*/src 2>/dev/null; then
  fail "Service Key referenciado en frontend src/"
else
  ok "Service Key ausente de frontend src/"
fi

echo "── 3. Anon Key NO está en backend ──────────────────────────────────"
if grep -rq "VITE_SUPABASE_ANON_KEY" api-gateway/src ms-*/src 2>/dev/null; then
  fail "Anon Key referenciada en backend"
else
  ok "Anon Key sólo en frontend"
fi

echo "── 4. Logs no incluyen tokens completos ────────────────────────────"
if grep -rE 'log\.(info|debug|warn).*(Bearer|access_token|refresh_token).*\$\{' \
   api-gateway/src ms-*/src 2>/dev/null | grep -v test; then
  fail "Posible leak de tokens en logs"
else
  ok "No se detectan logs con tokens interpolados"
fi

echo "── 5. RLS activo en user_profiles ──────────────────────────────────"
grep -q "ENABLE ROW LEVEL SECURITY" supabase/migrations/00001_create_user_profiles.sql \
  && ok "RLS habilitado" || fail "RLS no encontrado en migración"

echo "── 6. Webhook secret configurable ──────────────────────────────────"
grep -q "SUPABASE_WEBHOOK_SECRET" api-gateway/src/main/resources/application.yml \
  && ok "Webhook secret parametrizado" || fail "Webhook secret hardcodeado o ausente"

echo
echo "Resumen: $PASS pass / $FAIL fail"
exit $(( FAIL > 0 ? 1 : 0 ))
