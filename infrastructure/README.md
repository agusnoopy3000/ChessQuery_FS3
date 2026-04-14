# ChessQuery – Infraestructura local

Stack de desarrollo local para la plataforma ChessQuery. Levanta todas las dependencias de infraestructura usando Docker Compose.

## Requisitos

| Herramienta | Versión mínima |
|-------------|---------------|
| Docker      | 24.x          |
| Docker Compose | v2.x (plugin integrado) |
| Make        | cualquiera    |

## Setup rápido

```bash
cd infrastructure

# 1. Crear .env desde la plantilla (solo la primera vez)
cp .env.example .env

# 2. Levantar todo
make up
```

> `make up` copia `.env.example` → `.env` automáticamente si no existe.

---

## Servicios disponibles

| Servicio | Puerto(s) | URL / Conexión |
|---|---|---|
| auth_db (PostgreSQL) | 5432 | `jdbc:postgresql://localhost:5432/auth_db` |
| user_db (PostgreSQL) | 5433 | `jdbc:postgresql://localhost:5433/user_db` |
| tournament_db (PostgreSQL) | 5434 | `jdbc:postgresql://localhost:5434/tournament_db` |
| game_db (PostgreSQL) | 5435 | `jdbc:postgresql://localhost:5435/game_db` |
| analytics_db (PostgreSQL) | 5436 | `jdbc:postgresql://localhost:5436/analytics_db` |
| notif_db (PostgreSQL) | 5437 | `jdbc:postgresql://localhost:5437/notif_db` |
| etl_db (PostgreSQL) | 5438 | `jdbc:postgresql://localhost:5438/etl_db` |
| RabbitMQ AMQP | 5672 | `amqp://chessquery:chessquery_dev@localhost:5672/` |
| RabbitMQ Management | 15672 | http://localhost:15672 |
| Redis | 6379 | `redis://:chessquery_dev@localhost:6379` |
| MinIO S3 API | 9000 | http://localhost:9000 |
| MinIO Console | 9001 | http://localhost:9001 |
| Nginx | 80 | http://localhost |

---

## Credenciales por defecto

| Servicio | Usuario | Password |
|---|---|---|
| PostgreSQL (todas las DBs) | `chessquery` | `chessquery_dev` |
| RabbitMQ | `chessquery` | `chessquery_dev` |
| Redis | — | `chessquery_dev` |
| MinIO / S3 | `minioadmin` | `minioadmin` |

---

## Topología de mensajería (RabbitMQ)

Exchange **`ChessEvents`** tipo **Topic**:

```
ChessEvents (topic exchange)
 ├── user.*        → user.events
 ├── tournament.*  → tournament.events
 ├── player.*      → tournament.events
 ├── game.*        → game.events
 ├── elo.*         → game.events
 ├── etl.*         → etl.events
 └── rating.*      → etl.events
```

El script `scripts/init-rabbitmq.sh` se ejecuta automáticamente en un contenedor auxiliar la primera vez que se levanta el stack.

---

## Object Storage – Compatibilidad MinIO / AWS S3

Los microservicios usan el **AWS SDK** estándar. En local apuntan a MinIO; en producción apuntan a S3 real sin cambiar código.

**Configuración del SDK en local:**

```properties
# Java (application.properties / application.yml)
cloud.aws.s3.endpoint=http://localhost:9000
cloud.aws.credentials.access-key=${S3_ACCESS_KEY}
cloud.aws.credentials.secret-key=${S3_SECRET_KEY}
cloud.aws.region.static=${S3_REGION}
spring.cloud.aws.s3.path-style-access-enabled=true

# O con AWS SDK v2 directamente:
S3_ENDPOINT_URL=http://localhost:9000
AWS_ACCESS_KEY_ID=minioadmin
AWS_SECRET_ACCESS_KEY=minioadmin
AWS_REGION=us-east-1
```

**Migrar a AWS S3 real:**
1. Eliminar `S3_ENDPOINT_URL` del `.env` de producción.
2. Reemplazar `S3_ACCESS_KEY` / `S3_SECRET_KEY` por las credenciales IAM.
3. Deshabilitar `path-style-access` (S3 usa virtual-hosted style por defecto).
4. Crear el bucket `chessquery-pgn` en la cuenta AWS.

---

## Comandos Make

```bash
make up           # Levanta todos los servicios
make down         # Detiene los contenedores (preserva datos)
make logs         # Tail de logs en tiempo real
make reset        # Borra contenedores + volumes (¡pérdida de datos!)
make ps           # Estado de los contenedores
make clean-setup  # Re-ejecuta setup de RabbitMQ y MinIO
```

---

## Estructura del directorio

```
infrastructure/
├── docker-compose.yml        # Stack completo
├── .env.example              # Plantilla de variables de entorno
├── .env                      # Variables locales (no commitear)
├── Makefile                  # Comandos de gestión
├── nginx/
│   └── nginx.conf            # Configuración del reverse proxy
├── scripts/
│   ├── init-rabbitmq.sh      # Crea exchange, colas y bindings
│   └── init-minio.sh         # Crea el bucket S3
└── README.md
```

---

## Añadir el frontend

Cuando el build del frontend esté disponible, descomentar en `docker-compose.yml`:

```yaml
# nginx:
#   volumes:
#     - ../frontend/dist:/usr/share/nginx/html:ro
```

Y en `nginx/nginx.conf` ajustar el `root` si el path cambia.

---

## Añadir microservicios

Los microservicios Java se incorporarán con sus propios `Dockerfile` en cada módulo. Solo necesitarán:

1. Conectarse a la red `chessquery-net`.
2. Usar los hostnames internos (`auth_db`, `rabbitmq`, `redis`, `minio`).
3. Referenciarse en el compose principal o en un compose override.

```yaml
# Ejemplo de microservicio futuro
auth-service:
  build: ../auth-service
  networks:
    - chessquery-net
  environment:
    SPRING_DATASOURCE_URL: jdbc:postgresql://auth_db:5432/auth_db
    SPRING_DATASOURCE_USERNAME: ${DB_USER}
    SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
    SPRING_RABBITMQ_HOST: rabbitmq
    SPRING_REDIS_HOST: redis
```
