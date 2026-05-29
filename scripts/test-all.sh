#!/usr/bin/env bash
# ChessQuery — corre TODA la suite de pruebas (Java + BFFs + Frontend).
# Uso:   bash scripts/test-all.sh
# Pre:   JDK 17, Maven 3.9+, Node 20+, npm install hecho en cada workspace JS.
#
# Al terminar imprime una tabla-resumen con el conteo real de tests por módulo
# y el gran total. La tabla solo aparece si TODO pasó (set -e aborta al primer
# fallo, que es lo que querés ver si algo se rompe).

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if ! java -version 2>&1 | grep -q 'version "17'; then
  echo "❌ Se requiere JDK 17 (actual: $(java -version 2>&1 | head -1))"
  echo "   Exporta JAVA_HOME=/ruta/jdk-17 y PATH antes de correr."
  exit 1
fi

JAVA_MODULES=(api-gateway ms-users ms-tournament ms-game ms-notifications ms-analytics)
NODE_BFFS=(bff-player bff-organizer)
FRONTEND_APPS=(frontend/apps/chess-portal frontend/apps/organizer-panel)

# Metadata para la tabla final (capa + tipo de prueba por módulo).
declare -A LAYER TYPE COUNT
LAYER[api-gateway]="Backend Java";   TYPE[api-gateway]="Unit + filtro JWT (HS256/ES256)"
LAYER[ms-users]="Backend Java";      TYPE[ms-users]="Unit + Integración H2"
LAYER[ms-tournament]="Backend Java"; TYPE[ms-tournament]="Unit + Integración H2"
LAYER[ms-game]="Backend Java";       TYPE[ms-game]="Unit + Integración H2"
LAYER[ms-notifications]="Backend Java"; TYPE[ms-notifications]="Unit + Integración H2"
LAYER[ms-analytics]="Backend Java";  TYPE[ms-analytics]="Unit + Integración H2"
LAYER[bff-player]="BFF (NestJS)";    TYPE[bff-player]="Unit service + http (Jest)"
LAYER[bff-organizer]="BFF (NestJS)"; TYPE[bff-organizer]="Unit service + http (Jest)"
LAYER[chess-portal]="Frontend";      TYPE[chess-portal]="Page specs (Vitest + RTL)"
LAYER[organizer-panel]="Frontend";   TYPE[organizer-panel]="Page specs (Vitest + RTL)"

# Orden de filas en la tabla.
ROW_ORDER=()

# Cuenta los tests Java de un módulo desde sus reportes Surefire.
count_java() {
  grep -rhoE 'Tests run: [0-9]+' "$1"/target/surefire-reports/*.txt 2>/dev/null \
    | grep -oE '[0-9]+$' | awk '{s+=$1} END{print s+0}'
}

# Extrae el conteo de tests de un log de Jest ("Tests: N passed") o Vitest
# ("Tests  N passed"). Devuelve 0 si no encuentra.
count_node() {
  echo "$1" | grep -oE 'Tests:?[[:space:]]+[0-9]+ passed' | grep -oE '[0-9]+' | head -1 || true
}

echo "============================================================"
echo "  1/3  Microservicios Java (Spring Boot + JaCoCo)"
echo "============================================================"
for mod in "${JAVA_MODULES[@]}"; do
  echo ">>> $mod"
  (cd "$mod" && mvn -B -q clean test)
  COUNT[$mod]=$(count_java "$mod"); ROW_ORDER+=("$mod")
  echo "    $mod → ${COUNT[$mod]} tests"
done

echo "============================================================"
echo "  2/3  BFFs (NestJS + Jest)"
echo "============================================================"
for bff in "${NODE_BFFS[@]}"; do
  echo ">>> $bff"
  log=$(cd "$bff" && npm test --silent 2>&1); echo "$log"
  COUNT[$bff]=$(count_node "$log"); ROW_ORDER+=("$bff")
done

echo "============================================================"
echo "  3/3  Frontend (Vite + Vitest + RTL)"
echo "============================================================"
for app in "${FRONTEND_APPS[@]}"; do
  name="${app##*/}"
  echo ">>> $app"
  log=$(cd "$app" && npm test --silent 2>&1); echo "$log"
  COUNT[$name]=$(count_node "$log"); ROW_ORDER+=("$name")
done

# ─────────────────────────── Tabla-resumen ──────────────────────────────────
# Padding por glifos (no por bytes): ${#s} cuenta caracteres en locale UTF-8,
# así las celdas con acentos (Integración) quedan alineadas.
pad()  { local s="$1" w="$2" n=$(( $2 - ${#1} )); (( n<0 )) && n=0; printf '%s%*s' "$s" "$n" ""; }
rpad() { local s="$1" w="$2" n=$(( $2 - ${#1} )); (( n<0 )) && n=0; printf '%*s%s' "$n" "" "$s"; }
rep()  { local i out=""; for ((i=0; i<$2; i++)); do out+="$1"; done; printf '%s' "$out"; }

W1=16; W2=13; W3=33; W4=5
row() { printf '%s │ %s │ %s │ %s\n' "$(pad "$1" $W1)" "$(pad "$2" $W2)" "$(pad "$3" $W3)" "$(rpad "$4" $W4)"; }
sep() { printf '%s%s%s%s%s%s%s\n' "$(rep ─ $((W1+1)))" "$1" "$(rep ─ $((W2+2)))" "$1" "$(rep ─ $((W3+2)))" "$1" "$(rep ─ $((W4+1)))"; }

total=0
for k in "${ROW_ORDER[@]}"; do total=$((total + ${COUNT[$k]:-0})); done

echo
echo "============================================================"
echo "  RESUMEN DE LA SUITE — ChessQuery"
echo "============================================================"
row "Módulo" "Capa" "Tipo de prueba" "Tests"
sep "┼"
for k in "${ROW_ORDER[@]}"; do
  row "$k" "${LAYER[$k]}" "${TYPE[$k]}" "${COUNT[$k]}"
done
sep "┴"
printf '%s   %s   %s   %s\n' "$(pad "TOTAL" $W1)" "$(pad "" $W2)" "$(pad "" $W3)" "$(rpad "$total" $W4)"
echo "============================================================"
echo "✅ $total tests, 0 fallos, 0 errores."
echo "   Reportes de cobertura: <módulo>/target/site/jacoco/index.html (Java)"
echo "                          frontend/apps/<app>/coverage/index.html (Vitest)"
