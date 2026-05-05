#!/bin/bash
# Test: Bug Condition Exploration - Referencias a Servicios No Operativos
# Este test DEBE FALLAR en código sin corregir (confirma que el bug existe)
# Después del fix, DEBE PASAR (confirma que el bug está corregido)
#
# Requirements: 1.1, 1.2, 1.3, 1.4, 1.5

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
echo "Bug Condition Exploration Test"
echo "Archivo: $FILE"
echo "========================================"
echo ""

# --- Test 1: No debe haber referencias a "ms-analytics" excepto en contexto de "servicios no operativos" ---
echo "--- Test 1: Referencias a ms-analytics ---"
# Buscar ms-analytics ignorando la sección de alcance operativo
MS_ANALYTICS_REFS=$(grep -in "ms-analytics\|ms_analytics" "$FILE" | grep -iv "no operativo\|fuera de alcance\|fuera del alcance\|NO operativo\|ms-analytics, bff-admin, admin-panel" || true)
if [ -z "$MS_ANALYTICS_REFS" ]; then
  pass "No hay referencias a ms-analytics fuera del contexto de servicios no operativos"
else
  fail "Se encontraron referencias a ms-analytics fuera de contexto permitido" "$MS_ANALYTICS_REFS"
fi

# --- Test 2: No debe haber referencias a "bff-admin" excepto en contexto de "servicios no operativos" ---
echo "--- Test 2: Referencias a bff-admin ---"
BFF_ADMIN_REFS=$(grep -in "bff-admin\|bff_admin" "$FILE" | grep -iv "no operativo\|fuera de alcance\|fuera del alcance\|NO operativo\|ms-analytics, bff-admin, admin-panel" || true)
if [ -z "$BFF_ADMIN_REFS" ]; then
  pass "No hay referencias a bff-admin fuera del contexto de servicios no operativos"
else
  fail "Se encontraron referencias a bff-admin fuera de contexto permitido" "$BFF_ADMIN_REFS"
fi

# --- Test 3: No debe haber referencias a "admin-panel" excepto en contexto de "servicios no operativos" ---
echo "--- Test 3: Referencias a admin-panel ---"
ADMIN_PANEL_REFS=$(grep -in "admin-panel\|admin_panel" "$FILE" | grep -iv "no operativo\|fuera de alcance\|fuera del alcance\|NO operativo\|ms-analytics, bff-admin, admin-panel" || true)
if [ -z "$ADMIN_PANEL_REFS" ]; then
  pass "No hay referencias a admin-panel fuera del contexto de servicios no operativos"
else
  fail "Se encontraron referencias a admin-panel fuera de contexto permitido" "$ADMIN_PANEL_REFS"
fi

# --- Test 4: No debe haber rutas "/admin" en contexto de UI ---
echo "--- Test 4: Rutas /admin en contexto de UI ---"
# Buscar /admin como ruta UI (no como parte de una URL de API o mención de alcance)
ADMIN_ROUTES=$(grep -n '`/admin' "$FILE" | grep -iv "no operativo\|fuera de alcance\|fuera del alcance" || true)
if [ -z "$ADMIN_ROUTES" ]; then
  pass "No hay rutas /admin en contexto de UI"
else
  fail "Se encontraron rutas /admin en contexto de UI" "$ADMIN_ROUTES"
fi

# --- Test 5: Listas de servicios Java NO deben incluir ms-analytics ---
echo "--- Test 5: Listas de servicios Java con ms-analytics ---"
# Buscar listas que contengan ms-analytics junto con otros microservicios (Actuator, Prometheus)
JAVA_SERVICE_LISTS=$(grep -n "ms-auth.*ms-analytics\|ms-analytics.*ms-auth\|ms-game.*ms-analytics\|ms-analytics.*ms-game" "$FILE" || true)
if [ -z "$JAVA_SERVICE_LISTS" ]; then
  pass "Listas de servicios Java no incluyen ms-analytics"
else
  fail "Listas de servicios Java incluyen ms-analytics incorrectamente" "$JAVA_SERVICE_LISTS"
fi

# --- Test 6: No debe haber "Escena E" como sección ---
echo "--- Test 6: Existencia de Escena E ---"
ESCENA_E=$(grep -n "Escena E" "$FILE" || true)
if [ -z "$ESCENA_E" ]; then
  pass "No existe Escena E en el documento"
else
  fail "Escena E todavía existe en el documento" "$ESCENA_E"
fi

# --- Test 7: No debe haber referencias a vistas frontend fuera de alcance ---
echo "--- Test 7: Referencias a vistas frontend fuera de alcance ---"
# Buscar en contexto de flujos UI (no en notas aclaratorias)
SEARCH_VIEW_REFS=$(grep -n 'Flujo UI.*\/search\|Flujo UI.*\/rankings\|`/search`\|`/rankings`' "$FILE" | grep -iv "no está\|no están\|no disponible\|backend\|interno\|futuro" || true)
if [ -z "$SEARCH_VIEW_REFS" ]; then
  pass "No hay referencias a vistas /search o /rankings como vistas UI activas"
else
  fail "Se encontraron referencias a vistas frontend fuera de alcance" "$SEARCH_VIEW_REFS"
fi

# --- Test 8: Verificar que no se referencia "Bloquea escena E" ---
echo "--- Test 8: Referencias a Escena E bloqueante ---"
BLOCK_E_REFS=$(grep -in "bloquea escena E\|escena E" "$FILE" || true)
if [ -z "$BLOCK_E_REFS" ]; then
  pass "No hay referencias bloqueantes a Escena E"
else
  fail "Todavía existen referencias a Escena E" "$BLOCK_E_REFS"
fi

# --- Test 9: Verificar conteo de escenas (debe ser 5: A, B, C, D, F) ---
echo "--- Test 9: Conteo de escenas ---"
ESCENA_COUNT=$(grep -c "^### Escena " "$FILE" || true)
if [ "$ESCENA_COUNT" -eq 5 ]; then
  pass "El documento tiene exactamente 5 escenas (A, B, C, D, F)"
else
  fail "El documento tiene $ESCENA_COUNT escenas en lugar de 5" "Escenas encontradas: $(grep '^### Escena ' "$FILE")"
fi

echo ""
echo "========================================"
echo "Resultados: $((TOTAL_TESTS - FAILURES))/$TOTAL_TESTS tests pasaron"
if [ "$FAILURES" -gt 0 ]; then
  echo -e "${RED}$FAILURES test(s) FALLARON${NC}"
  echo "Estado: FAIL (el bug existe en el código sin corregir)"
  exit 1
else
  echo -e "${GREEN}Todos los tests pasaron${NC}"
  echo "Estado: PASS (el bug ha sido corregido)"
  exit 0
fi
