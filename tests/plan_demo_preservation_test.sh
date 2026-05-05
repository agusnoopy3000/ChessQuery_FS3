#!/bin/bash
# Test: Preservation Property - Contenido de Servicios Operativos
# Este test DEBE PASAR en código sin corregir (confirma el comportamiento base a preservar)
# Después del fix, también DEBE PASAR (confirma que no hay regresiones)
#
# Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7

set -euo pipefail

FILE="PLAN_DEMO.md"
FAILURES=0
TOTAL_TESTS=0

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

pass() {
  TOTAL_TESTS=$((TOTAL_TESTS + 1))
  echo -e "${GREEN}✅ PASS${NC}: $1"
}

fail() {
  TOTAL_TESTS=$((TOTAL_TESTS + 1))
  FAILURES=$((FAILURES + 1))
  echo -e "${RED}❌ FAIL${NC}: $1"
  echo -e "  ${YELLOW}Detalle:${NC} $2"
}

echo "========================================"
echo "Preservation Property Tests"
echo "Archivo: $FILE"
echo "========================================"
echo ""

# --- Test 1: Escena A permanece completa ---
echo "--- Test 1: Preservación de Escena A (Registro y login) ---"
ESCENA_A=$(grep -c "Escena A" "$FILE" || true)
if [ "$ESCENA_A" -ge 1 ]; then
  pass "Escena A existe en el documento"
else
  fail "Escena A no se encontró" "La sección fue eliminada incorrectamente"
fi

# Verificar endpoints de Escena A
ENDPOINTS_A=0
grep -q "POST /auth/register" "$FILE" && ENDPOINTS_A=$((ENDPOINTS_A + 1))
grep -q "POST /auth/login" "$FILE" && ENDPOINTS_A=$((ENDPOINTS_A + 1))
grep -q "GET /auth/validate" "$FILE" && ENDPOINTS_A=$((ENDPOINTS_A + 1))
grep -q "GET /player/me/dashboard" "$FILE" && ENDPOINTS_A=$((ENDPOINTS_A + 1))
if [ "$ENDPOINTS_A" -eq 4 ]; then
  pass "Escena A tiene sus 4 endpoints completos"
else
  fail "Escena A tiene $ENDPOINTS_A/4 endpoints" "Faltan endpoints de registro/login"
fi

# Verificar flujo UI de Escena A
if grep -q "chess-portal.*register.*login.*portal\|register.*→.*login.*→.*portal" "$FILE"; then
  pass "Escena A tiene flujo UI documentado"
else
  fail "Escena A no tiene flujo UI correcto" "Debe incluir chess-portal /register → /login → /portal"
fi

# Verificar riesgos de Escena A
if grep -q "user.registered.*no llegue a ms-users\|user\.registered.*ms-users" "$FILE"; then
  pass "Escena A tiene riesgos documentados"
else
  fail "Escena A no tiene riesgos documentados" "Debe mencionar riesgo de user.registered"
fi

# --- Test 2: Escena C permanece completa ---
echo ""
echo "--- Test 2: Preservación de Escena C (Torneo end-to-end) ---"
ESCENA_C=$(grep -c "Escena C" "$FILE" || true)
if [ "$ESCENA_C" -ge 1 ]; then
  pass "Escena C existe en el documento"
else
  fail "Escena C no se encontró" "La sección fue eliminada incorrectamente"
fi

# Verificar endpoints de Escena C
ENDPOINTS_C=0
grep -q "POST /organizer/tournaments" "$FILE" && ENDPOINTS_C=$((ENDPOINTS_C + 1))
grep -q "POST /organizer/tournaments/:id/join" "$FILE" && ENDPOINTS_C=$((ENDPOINTS_C + 1))
grep -q "POST /organizer/tournaments/:id/rounds/1/generate" "$FILE" && ENDPOINTS_C=$((ENDPOINTS_C + 1))
grep -q "PATCH /organizer/pairings/:pid/result" "$FILE" && ENDPOINTS_C=$((ENDPOINTS_C + 1))
grep -q "GET /organizer/tournaments/:id/standings" "$FILE" && ENDPOINTS_C=$((ENDPOINTS_C + 1))
if [ "$ENDPOINTS_C" -eq 5 ]; then
  pass "Escena C tiene sus 5 endpoints completos"
else
  fail "Escena C tiene $ENDPOINTS_C/5 endpoints" "Faltan endpoints de torneo"
fi

# Verificar riesgos de Escena C
if grep -q "circuit breaker.*MS-Tournament\|MS-Tournament.*MS-Users" "$FILE"; then
  pass "Escena C tiene riesgos documentados"
else
  fail "Escena C no tiene riesgos" "Debe mencionar circuit breaker"
fi

# --- Test 3: Escena D permanece completa ---
echo ""
echo "--- Test 3: Preservación de Escena D (Partida y PGN) ---"
ESCENA_D=$(grep -c "Escena D" "$FILE" || true)
if [ "$ESCENA_D" -ge 1 ]; then
  pass "Escena D existe en el documento"
else
  fail "Escena D no se encontró" "La sección fue eliminada incorrectamente"
fi

# Verificar endpoints de Escena D
ENDPOINTS_D=0
grep -q "POST /games" "$FILE" && ENDPOINTS_D=$((ENDPOINTS_D + 1))
grep -q "GET /games/{id}/pgn-url" "$FILE" && ENDPOINTS_D=$((ENDPOINTS_D + 1))
grep -q "GET /games?playerId=" "$FILE" && ENDPOINTS_D=$((ENDPOINTS_D + 1))
if [ "$ENDPOINTS_D" -eq 3 ]; then
  pass "Escena D tiene sus 3 endpoints completos"
else
  fail "Escena D tiene $ENDPOINTS_D/3 endpoints" "Faltan endpoints de partida/PGN"
fi

# Verificar riesgos de Escena D
if grep -q "MinIO bucket.*500\|500.*MinIO\|MinIO.*crear" "$FILE"; then
  pass "Escena D tiene riesgos documentados (MinIO)"
else
  fail "Escena D no tiene riesgos de MinIO" "Debe mencionar riesgo de bucket sin crear"
fi

# --- Test 4: Escena F permanece completa ---
echo ""
echo "--- Test 4: Preservación de Escena F (Notificaciones) ---"
ESCENA_F=$(grep -c "Escena F" "$FILE" || true)
if [ "$ESCENA_F" -ge 1 ]; then
  pass "Escena F existe en el documento"
else
  fail "Escena F no se encontró" "La sección fue eliminada incorrectamente"
fi

# Verificar contenido de Escena F
if grep -q "RabbitMQ Management UI\|notification_log" "$FILE"; then
  pass "Escena F tiene verificaciones de RabbitMQ/notification_log"
else
  fail "Escena F no tiene verificaciones correctas" "Debe mencionar RabbitMQ Management UI y notification_log"
fi

if grep -q "USER_WELCOME" "$FILE" && grep -q "GAME_RESULT" "$FILE"; then
  pass "Escena F tiene tipos de notificación documentados"
else
  fail "Escena F no tiene tipos de notificación" "Debe mencionar USER_WELCOME y GAME_RESULT"
fi

# --- Test 5: Monitoreo de rendimiento mantiene estructura ---
echo ""
echo "--- Test 5: Preservación de sección Monitoreo ---"
if grep -q "Monitoreo de rendimiento" "$FILE"; then
  pass "Sección de monitoreo existe"
else
  fail "Sección de monitoreo no existe" "Fue eliminada incorrectamente"
fi

# Verificar los 5 pasos
PASOS=0
grep -q "Paso 1.*Actuator" "$FILE" && PASOS=$((PASOS + 1))
grep -q "Paso 2.*Prometheus.*Grafana\|Paso 2.*Stack" "$FILE" && PASOS=$((PASOS + 1))
grep -q "Paso 3.*Dashboard.*Grafana\|Paso 3.*Grafana" "$FILE" && PASOS=$((PASOS + 1))
grep -q "Paso 4.*Healthchecks\|Paso 4.*healthchecks" "$FILE" && PASOS=$((PASOS + 1))
grep -q "Paso 5.*Logs" "$FILE" && PASOS=$((PASOS + 1))
if [ "$PASOS" -eq 5 ]; then
  pass "Los 5 pasos de monitoreo están presentes"
else
  fail "Solo $PASOS/5 pasos de monitoreo están presentes" "Deben existir los 5 pasos"
fi

# Verificar configuración XML de Actuator
if grep -q "spring-boot-starter-actuator" "$FILE"; then
  pass "Configuración XML de Actuator presente"
else
  fail "Configuración XML de Actuator eliminada" "La config de pom.xml debe mantenerse"
fi

# Verificar Prometheus en docker-compose
if grep -q "prom/prometheus" "$FILE" && grep -q "grafana/grafana" "$FILE"; then
  pass "Configuración de Prometheus y Grafana en docker-compose presente"
else
  fail "Configuración de Prometheus/Grafana falta" "Los bloques YAML deben mantenerse"
fi

# --- Test 6: Cronograma mantiene tabla de semanas ---
echo ""
echo "--- Test 6: Preservación de Cronograma ---"
if grep -q "Cronograma sugerido" "$FILE"; then
  pass "Sección de cronograma existe"
else
  fail "Sección de cronograma no existe" "Fue eliminada incorrectamente"
fi

if grep -q "Semana 1.*días 1-2" "$FILE" && grep -q "Semana 2.*días 5-6\|Semana 2.*días 3-4" "$FILE"; then
  pass "Tabla de semanas del cronograma está presente"
else
  fail "Tabla de semanas del cronograma está incompleta" "Deben existir entradas de Semana 1 y Semana 2"
fi

if grep -q "Día de la demo" "$FILE"; then
  pass "Entrada 'Día de la demo' presente en cronograma"
else
  fail "Entrada 'Día de la demo' falta" "La última fila del cronograma debe mantenerse"
fi

# --- Test 7: Checklist pre-demo mantiene items de infraestructura ---
echo ""
echo "--- Test 7: Preservación de Checklist (infraestructura) ---"
if grep -q "Checklist pre-demo" "$FILE"; then
  pass "Sección de checklist existe"
else
  fail "Sección de checklist no existe" "Fue eliminada incorrectamente"
fi

INFRA_ITEMS=0
grep -q "MinIO bucket.*chessquery-pgn\|chessquery-pgn" "$FILE" && INFRA_ITEMS=$((INFRA_ITEMS + 1))
grep -q "RabbitMQ exchange.*ChessEvents\|ChessEvents.*queues" "$FILE" && INFRA_ITEMS=$((INFRA_ITEMS + 1))
grep -q "Seed de 10 jugadores\|10 jugadores chilenos" "$FILE" && INFRA_ITEMS=$((INFRA_ITEMS + 1))
grep -q "Seed de.*aperturas\|aperturas en game_db" "$FILE" && INFRA_ITEMS=$((INFRA_ITEMS + 1))
grep -q "Grafana.*localhost:3000\|localhost:3000.*dashboard" "$FILE" && INFRA_ITEMS=$((INFRA_ITEMS + 1))
grep -q "Prometheus.*localhost:9091\|localhost:9091" "$FILE" && INFRA_ITEMS=$((INFRA_ITEMS + 1))
grep -q "smoke-test.sh\|smoke test" "$FILE" && INFRA_ITEMS=$((INFRA_ITEMS + 1))
if [ "$INFRA_ITEMS" -ge 6 ]; then
  pass "Checklist tiene $INFRA_ITEMS/7 items de infraestructura preservados"
else
  fail "Checklist tiene solo $INFRA_ITEMS/7 items de infraestructura" "Deben mantenerse los items de MinIO, RabbitMQ, seeds, Grafana, Prometheus, smoke test"
fi

# --- Test 8: Regla de oro permanece sin cambios ---
echo ""
echo "--- Test 8: Preservación de Regla de oro ---"
if grep -q "Regla de oro" "$FILE"; then
  pass "Sección 'Regla de oro' existe"
else
  fail "Sección 'Regla de oro' no existe" "Fue eliminada incorrectamente"
fi

if grep -q "T-5 días\|congelar main\|Desde T-5" "$FILE"; then
  pass "Contenido de 'Regla de oro' preservado"
else
  fail "Contenido de 'Regla de oro' alterado" "Debe mantener la referencia a T-5 días y congelar main"
fi

if grep -q "Solo bugfixes con PR y smoke test\|bugfixes.*PR.*smoke" "$FILE"; then
  pass "Detalle de 'Regla de oro' preservado"
else
  fail "Detalle de 'Regla de oro' alterado" "Debe mantener mención de bugfixes, PR, smoke test"
fi

echo ""
echo "========================================"
echo "Resultados: $((TOTAL_TESTS - FAILURES))/$TOTAL_TESTS tests pasaron"
if [ "$FAILURES" -gt 0 ]; then
  echo -e "${RED}$FAILURES test(s) FALLARON${NC}"
  echo "Estado: FAIL (regresión detectada)"
  exit 1
else
  echo -e "${GREEN}Todos los tests pasaron${NC}"
  echo "Estado: PASS (contenido de servicios operativos preservado)"
  exit 0
fi
