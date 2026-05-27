#!/usr/bin/env bash
# Ejecuta toda la batería de tests del repo y reporta cobertura.
# Falla (exit 1) si algún MS Java tiene line coverage < 70%.
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "${ROOT_DIR}"

JAVA_SERVICES=(ms-users ms-tournament ms-game ms-notifications)
BFFS=(bff-player bff-organizer bff-admin)

FAILED=()

run() {
  local label="$1"; shift
  echo
  echo "════════════════════════════════════════════════════════════"
  echo "▶ ${label}"
  echo "════════════════════════════════════════════════════════════"
  if ! "$@"; then
    FAILED+=("${label}")
  fi
}

# ── Java microservicios ─────────────────────────────────────────────
for ms in "${JAVA_SERVICES[@]}"; do
  if [[ -d "${ms}" ]]; then
    run "Java/${ms}" bash -c "cd '${ms}' && mvn -q test 2>&1 | tee '../${ms}-coverage.log' | tail -40"
  fi
done

# ── ms-etl (Python) ─────────────────────────────────────────────────
if [[ -d "ms-etl" ]]; then
  run "Python/ms-etl" bash -c "cd ms-etl && pytest --cov=app --cov-report=term-missing 2>&1 | tail -40"
fi

# ── BFFs (Jest) ─────────────────────────────────────────────────────
for bff in "${BFFS[@]}"; do
  if [[ -d "${bff}" ]]; then
    run "Node/${bff}" bash -c "cd '${bff}' && (test -d node_modules || npm install --no-audit --no-fund) && npm test -- --coverage 2>&1 | tail -40"
  fi
done

# ── Reporte de cobertura JaCoCo agregada ────────────────────────────
echo
echo "════════════════════════════════════════════════════════════"
echo "▶ Reporte de cobertura (JaCoCo)"
echo "════════════════════════════════════════════════════════════"
if [[ -f parse_coverage.py ]]; then
  python3 parse_coverage.py | tee /tmp/coverage-summary.txt
else
  echo "⚠ parse_coverage.py no encontrado"
fi

# ── Gate de cobertura ≥ 70% por servicio Java ───────────────────────
THRESHOLD=70
GATE_FAILED=0
if [[ -f /tmp/coverage-summary.txt ]]; then
  while read -r line; do
    if [[ "${line}" =~ Line\ Coverage:\ ([0-9.]+)% ]]; then
      pct="${BASH_REMATCH[1]}"
      pct_int="${pct%.*}"
      if (( pct_int < THRESHOLD )); then
        echo "✗ Cobertura bajo umbral: ${line}"
        GATE_FAILED=1
      fi
    fi
  done < /tmp/coverage-summary.txt
fi

echo
if [[ ${#FAILED[@]} -gt 0 ]]; then
  echo "✗ Tests con fallas:"
  for f in "${FAILED[@]}"; do echo "  - ${f}"; done
fi
if (( GATE_FAILED == 1 )); then
  echo "✗ Cobertura < ${THRESHOLD}% en al menos un servicio."
  exit 1
fi
if [[ ${#FAILED[@]} -gt 0 ]]; then
  exit 1
fi
echo "✅ Todos los tests pasaron y cumplen cobertura ≥ ${THRESHOLD}%."
