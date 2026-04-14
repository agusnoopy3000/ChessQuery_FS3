#!/bin/sh
# =============================================================================
# init-rabbitmq.sh
# Crea el exchange "ChessEvents" (Topic) y las 4 colas con sus bindings
# usando rabbitmqadmin (incluido en la imagen management).
#
# Variables de entorno esperadas:
#   RABBITMQ_HOST      – hostname del broker  (default: rabbitmq)
#   RABBITMQ_USER      – usuario admin        (default: chessquery)
#   RABBITMQ_PASSWORD  – contraseña           (default: chessquery_dev)
# =============================================================================

set -e

HOST="${RABBITMQ_HOST:-rabbitmq}"
USER="${RABBITMQ_USER:-chessquery}"
PASS="${RABBITMQ_PASSWORD:-chessquery_dev}"
MGMT_PORT="15672"

ADMIN="rabbitmqadmin --host=${HOST} --port=${MGMT_PORT} --username=${USER} --password=${PASS}"

echo "[init-rabbitmq] Esperando a que la API de management esté disponible..."
until $ADMIN list exchanges > /dev/null 2>&1; do
  echo "[init-rabbitmq] Reintentando en 5 s..."
  sleep 5
done

echo "[init-rabbitmq] Broker listo. Configurando topología..."

# ---------------------------------------------------------------------------
# Exchange principal
# ---------------------------------------------------------------------------
$ADMIN declare exchange \
  name="ChessEvents" \
  type="topic" \
  durable=true \
  auto_delete=false

echo "[init-rabbitmq] Exchange 'ChessEvents' creado."

# ---------------------------------------------------------------------------
# Colas
# ---------------------------------------------------------------------------
for QUEUE in user.events tournament.events game.events etl.events; do
  $ADMIN declare queue \
    name="${QUEUE}" \
    durable=true \
    auto_delete=false
  echo "[init-rabbitmq] Cola '${QUEUE}' creada."
done

# ---------------------------------------------------------------------------
# Bindings
#
# user.events        → user.*
# tournament.events  → tournament.*  y  player.*
# game.events        → game.*        y  elo.*
# etl.events         → etl.*         y  rating.*
# ---------------------------------------------------------------------------

bind() {
  QUEUE="$1"
  ROUTING_KEY="$2"
  $ADMIN declare binding \
    source="ChessEvents" \
    destination_type="queue" \
    destination="${QUEUE}" \
    routing_key="${ROUTING_KEY}"
  echo "[init-rabbitmq] Binding '${ROUTING_KEY}' -> '${QUEUE}'."
}

bind "user.events"       "user.*"
bind "tournament.events" "tournament.*"
bind "tournament.events" "player.*"
bind "game.events"       "game.*"
bind "game.events"       "elo.*"
bind "etl.events"        "etl.*"
bind "etl.events"        "rating.*"

echo "[init-rabbitmq] Topología configurada correctamente."