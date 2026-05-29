#!/usr/bin/env bash
# ChessQuery — corre TODA la suite de pruebas (Java + BFFs + Frontend).
# Uso:   bash scripts/test-all.sh
# Pre:   JDK 17, Maven 3.9+, Node 20+, npm install hecho en cada workspace JS.

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

echo "============================================================"
echo "  1/3  Microservicios Java (Spring Boot + JaCoCo)"
echo "============================================================"
for mod in "${JAVA_MODULES[@]}"; do
  echo ">>> $mod"
  (cd "$mod" && mvn -B -q clean test)
done

echo "============================================================"
echo "  2/3  BFFs (NestJS + Jest)"
echo "============================================================"
for bff in "${NODE_BFFS[@]}"; do
  echo ">>> $bff"
  (cd "$bff" && npm test --silent)
done

echo "============================================================"
echo "  3/3  Frontend (Vite + Vitest + RTL)"
echo "============================================================"
for app in "${FRONTEND_APPS[@]}"; do
  echo ">>> $app"
  (cd "$app" && npm test --silent)
done

echo
echo "✅ Toda la suite pasó (~530 tests)."
