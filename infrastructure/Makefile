# =============================================================================
# ChessQuery – Makefile de infraestructura
# =============================================================================

COMPOSE  = docker compose
ENV_FILE = .env
DC_FILE  = docker-compose.yml

.PHONY: help up down logs reset ps clean-setup check-env

# ---------------------------------------------------------------------------
# Ayuda por defecto
# ---------------------------------------------------------------------------
help:
	@echo ""
	@echo "  ChessQuery – Comandos de infraestructura"
	@echo ""
	@echo "  make up          Levanta todos los servicios en background"
	@echo "  make down        Detiene y elimina los contenedores"
	@echo "  make logs        Muestra logs en tiempo real (Ctrl+C para salir)"
	@echo "  make reset       Detiene contenedores y BORRA todos los volumes"
	@echo "  make ps          Estado de los contenedores"
	@echo "  make clean-setup Re-ejecuta los contenedores de setup (rabbitmq/minio)"
	@echo ""

# ---------------------------------------------------------------------------
# check-env: copia .env.example → .env si no existe
# ---------------------------------------------------------------------------
check-env:
	@if [ ! -f $(ENV_FILE) ]; then \
		echo "[make] $(ENV_FILE) no encontrado. Copiando desde .env.example..."; \
		cp .env.example $(ENV_FILE); \
		echo "[make] Revisa y ajusta $(ENV_FILE) antes de continuar."; \
	fi

# ---------------------------------------------------------------------------
# up: Levanta la infraestructura (servicios) completa
# ---------------------------------------------------------------------------
up: check-env
	@echo "[make] Levantando infraestructura ChessQuery..."
	$(COMPOSE) --env-file $(ENV_FILE) -f $(DC_FILE) up -d --remove-orphans
	@echo ""
	@echo "  Servicios disponibles:"
	@echo "    PostgreSQL  auth_db       → localhost:5432"
	@echo "    PostgreSQL  user_db       → localhost:5433"
	@echo "    PostgreSQL  tournament_db → localhost:5434"
	@echo "    PostgreSQL  game_db       → localhost:5435"
	@echo "    PostgreSQL  analytics_db  → localhost:5436"
	@echo "    PostgreSQL  notif_db      → localhost:5437"
	@echo "    PostgreSQL  etl_db        → localhost:5438"
	@echo "    RabbitMQ    AMQP          → localhost:5672"
	@echo "    RabbitMQ    Management UI → http://localhost:15672"
	@echo "    Redis                     → localhost:6379"
	@echo "    MinIO       S3 API        → http://localhost:9000"
	@echo "    MinIO       Console       → http://localhost:9001"
	@echo "    Nginx       Proxy         → http://localhost:80"
	@echo ""

# ---------------------------------------------------------------------------
# down: detiene los contenedores sin borrar volumes
# ---------------------------------------------------------------------------
down:
	@echo "[make] Deteniendo contenedores..."
	$(COMPOSE) --env-file $(ENV_FILE) -f $(DC_FILE) down

# ---------------------------------------------------------------------------
# logs: tail de todos los servicios
# ---------------------------------------------------------------------------
logs:
	$(COMPOSE) --env-file $(ENV_FILE) -f $(DC_FILE) logs -f

# ---------------------------------------------------------------------------
# reset: detiene + borra TODOS los volumes (¡datos persistentes eliminados!)
# ---------------------------------------------------------------------------
reset:
	@echo "[make] ADVERTENCIA: esto eliminará todos los volumes de ChessQuery."
	@printf "  ¿Confirmar? [s/N] " && read ans && [ "$${ans:-N}" = "s" ]
	$(COMPOSE) --env-file $(ENV_FILE) -f $(DC_FILE) down -v --remove-orphans
	@echo "[make] Volumes eliminados."

# ---------------------------------------------------------------------------
# ps: estado actual de los contenedores
# ---------------------------------------------------------------------------
ps:
	$(COMPOSE) --env-file $(ENV_FILE) -f $(DC_FILE) ps

# ---------------------------------------------------------------------------
# clean-setup: recrea los contenedores one-shot de setup
# Útil si necesitas volver a crear el exchange/colas o el bucket.
# ---------------------------------------------------------------------------
clean-setup:
	@echo "[make] Re-ejecutando contenedores de setup..."
	$(COMPOSE) --env-file $(ENV_FILE) -f $(DC_FILE) rm -f rabbitmq_setup minio_setup
	$(COMPOSE) --env-file $(ENV_FILE) -f $(DC_FILE) up -d rabbitmq_setup minio_setup
	@echo "[make] Setup en progreso. Revisa logs con: make logs"