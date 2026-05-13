# =============================================================================
# ChessQuery – Makefile de infraestructura
# =============================================================================

COMPOSE  = docker compose
ENV_FILE = .env
DC_FILE  = docker-compose.yml

.PHONY: help up down logs reset ps clean-setup check-env build demo-reset demo-prebuild demo-up demo-down demo-supabase-trim demo-m1-up preflight

# ---------------------------------------------------------------------------
# Ayuda por defecto
# ---------------------------------------------------------------------------
help:
	@echo ""
	@echo "  ChessQuery – Comandos de infraestructura"
	@echo ""
	@echo "  make up          Reconstruye imágenes y levanta todos los servicios"
	@echo "  make build       Reconstruye imágenes Docker sin levantar (--no-cache)"
	@echo "  make demo-prebuild  Build SOLO scope demo (correr la noche antes)"
	@echo "  make demo-up        Up scope demo con heap reducido (M1 8GB ready)"
	@echo "  make demo-supabase-trim  Apaga contenedores Supabase no esenciales"
	@echo "  make demo-m1-up     Arranque completo recomendado para demo en M1"
	@echo "  make demo-down      Detener scope demo"
	@echo "  make preflight      Check 5 min antes de demo"
	@echo "  make demo-reset  Limpia datos de demo conservando los seeds"
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
# build: reconstruye imágenes Docker desde cero (--no-cache)
# ---------------------------------------------------------------------------
build: check-env
	@echo "[make] Reconstruyendo imágenes Docker (--no-cache)..."
	$(COMPOSE) --env-file $(ENV_FILE) -f $(DC_FILE) build --no-cache

# ---------------------------------------------------------------------------
# up: rebuild incremental + levantar todos los servicios
# (usa caché de capas; para rebuild total ejecuta `make build` antes)
# ---------------------------------------------------------------------------
up: check-env
	@echo "[make] Construyendo imágenes (cache) y levantando infraestructura..."
	$(COMPOSE) --env-file $(ENV_FILE) -f $(DC_FILE) up -d --build --remove-orphans
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
# demo-reset: limpia datos de demo conservando los seeds (jugadores chilenos,
# aperturas ECO). NO toca volumes — los containers siguen levantados.
# ---------------------------------------------------------------------------
demo-reset:
	@echo "[make] Reseteando datos de demo..."
	@./scripts/demo-reset.sh

# ---------------------------------------------------------------------------
# demo-prebuild: buildea SOLO los servicios de scope demo (la noche
# anterior). Saca de la ruta crítica el `--no-cache` del día de demo.
# ---------------------------------------------------------------------------
DEMO_SERVICES = ms-users ms-tournament ms-game ms-analytics ms-notifications \
                bff-player bff-organizer api-gateway

DEMO_INFRA    = user_db tournament_db game_db analytics_db notif_db \
                rabbitmq redis

DEMO_COMPOSE  = -f $(DC_FILE) -f docker-compose.demo.yml

demo-prebuild: check-env
	@echo "[make] Pre-build de servicios demo (cache)..."
	$(COMPOSE) --env-file $(ENV_FILE) $(DEMO_COMPOSE) build $(DEMO_SERVICES)

# ---------------------------------------------------------------------------
# demo-up: levanta SOLO scope demo con tope de heap optimizado para M1
# 8GB (ver docker-compose.demo.yml). Espera healthchecks y sale.
# ---------------------------------------------------------------------------
demo-up: check-env
	@echo "[make] Levantando stack de demo (heap reducido)..."
	$(COMPOSE) --env-file $(ENV_FILE) $(DEMO_COMPOSE) up -d --wait --no-build \
	    $(DEMO_INFRA) $(DEMO_SERVICES)
	@echo ""
	@echo "  Demo lista. Si login devuelve 504/502 tras restart de Supabase:"
	@echo "    docker restart supabase_kong_ChessQuery_FS3"

# ---------------------------------------------------------------------------
# demo-supabase-trim: apaga contenedores pesados de Supabase que no se usan
# durante la demo en M1 8GB, pero preserva Studio para poder mostrar
# Auth users y Storage/PGN. Se puede ejecutar repetidamente sin problema.
# ---------------------------------------------------------------------------
demo-supabase-trim:
	@echo "[make] Apagando contenedores Supabase no esenciales para la demo..."
	-@docker stop supabase_analytics_ChessQuery_FS3 >/dev/null 2>&1 || true
	-@docker stop supabase_edge_runtime_ChessQuery_FS3 >/dev/null 2>&1 || true
	-@docker stop supabase_vector_ChessQuery_FS3 >/dev/null 2>&1 || true
	@echo "[make] Supabase recortado. Studio se mantiene encendido para la demo."

# ---------------------------------------------------------------------------
# demo-m1-up: secuencia recomendada para la demo en MacBook Air M1 8GB.
# Requiere que Supabase ya haya sido iniciado con `supabase start`.
# ---------------------------------------------------------------------------
demo-m1-up: demo-supabase-trim demo-up
	@echo ""
	@echo "  Stack M1 listo:"
	@echo "    API Gateway      → http://localhost:8080"
	@echo "    Chess Portal     → http://localhost:5173  (levantar con npm run dev:portal)"
	@echo "    Organizer Panel  → http://localhost:5174  (levantar con npm run dev:organizer)"

# ---------------------------------------------------------------------------
# demo-down: detiene solo los contenedores de scope demo.
# ---------------------------------------------------------------------------
demo-down:
	$(COMPOSE) --env-file $(ENV_FILE) $(DEMO_COMPOSE) stop \
	    $(DEMO_INFRA) $(DEMO_SERVICES)

# ---------------------------------------------------------------------------
# preflight: corre el check de 5 min antes de demo
# ---------------------------------------------------------------------------
preflight:
	@bash $(CURDIR)/../scripts/preflight.sh

# ---------------------------------------------------------------------------
# clean-setup: recrea los contenedores one-shot de setup
# Útil si necesitas volver a crear el exchange/colas o el bucket.
# ---------------------------------------------------------------------------
clean-setup:
	@echo "[make] Re-ejecutando contenedores de setup..."
	$(COMPOSE) --env-file $(ENV_FILE) -f $(DC_FILE) rm -f rabbitmq_setup minio_setup
	$(COMPOSE) --env-file $(ENV_FILE) -f $(DC_FILE) up -d rabbitmq_setup minio_setup
	@echo "[make] Setup en progreso. Revisa logs con: make logs"
