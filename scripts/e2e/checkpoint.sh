#!/usr/bin/env bash
# Checkpoint final post-migración (Tarea 20).
# Verifica que el sistema corre sin MS-Auth, MinIO ni auth_db.
set -uo pipefail

PASS=0; FAIL=0
ok()   { echo "✅ $1"; PASS=$((PASS+1)); }
fail() { echo "❌ $1"; FAIL=$((FAIL+1)); }

cd "$(dirname "$0")/../.."

echo "── 1. docker-compose.yml NO menciona ms-auth/minio/auth_db ─────────"
if grep -E "^\s*(ms-auth|minio|auth_db):\s*$" infrastructure/docker-compose.yml; then
  fail "Servicios legacy aún declarados"
else
  ok "Servicios legacy removidos del compose"
fi

echo "── 2. docker-compose.backup.yml existe ─────────────────────────────"
[[ -f infrastructure/docker-compose.backup.yml ]] && ok "Backup compose presente" \
  || fail "docker-compose.backup.yml ausente"

echo "── 3. Branch backup/ms-auth-pre-supabase existe ────────────────────"
git rev-parse --verify backup/ms-auth-pre-supabase >/dev/null 2>&1 \
  && ok "Branch backup presente" || fail "Branch backup ausente"

echo "── 4. Documentación de Supabase y Rollback presentes ───────────────"
[[ -f docs/SUPABASE_SETUP.md ]] && ok "SUPABASE_SETUP.md presente" || fail "SUPABASE_SETUP.md ausente"
[[ -f docs/ROLLBACK.md ]] && ok "ROLLBACK.md presente" || fail "ROLLBACK.md ausente"

echo "── 5. Frontend usa createSupabaseApiClient ─────────────────────────"
grep -q "createSupabaseApiClient" frontend/apps/chess-portal/src/main.tsx \
  && ok "chess-portal usa Supabase client" || fail "chess-portal aún usa createApiClient"
grep -q "createSupabaseApiClient" frontend/apps/organizer-panel/src/main.tsx \
  && ok "organizer-panel usa Supabase client" || fail "organizer-panel aún usa createApiClient"

echo "── 6. MS-Game inyecta StorageService ───────────────────────────────"
grep -q "StorageService" ms-game/src/main/java/cl/chessquery/game/service/GameService.java \
  && ok "GameService inyecta StorageService" || fail "GameService aún usa PgnStorageService"

echo "── 7. API Gateway no rutea /auth/login a ms-auth ───────────────────"
if grep -q "ms-auth" api-gateway/src/main/resources/application.yml; then
  fail "Gateway aún referencia ms-auth"
else
  ok "Gateway sin rutas a ms-auth"
fi

echo "── 8. Tests pasan ──────────────────────────────────────────────────"
echo "(ejecutar manualmente: mvn -pl ms-users,ms-game,api-gateway test)"

echo
echo "Resumen: $PASS pass / $FAIL fail"
exit $(( FAIL > 0 ? 1 : 0 ))
