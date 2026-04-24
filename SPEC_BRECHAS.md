# SPEC — Cierre de brechas ChessQuery FS3

Fecha: 2026-04-23
Objetivo: llevar el sistema a estado "demo-ready" cerrando las 5 brechas identificadas. Trabajo en ramas aisladas, sin tocar servicios ya completados.

---

## Brecha 1 — MS-Analytics sin código fuente

### Estado actual
- `ms-analytics/` solo contiene `skills.md` y `target/classes/` (con `.class` compilados).
- Sin `src/main/java/`, `pom.xml`, ni `Dockerfile`. No se puede recompilar ni levantar en Docker.
- Clases existentes en `target/classes/cl/chessquery/analytics/`:
  - `AnalyticsApplication`, `config/RabbitMQConfig`, `controller/AnalyticsController`
  - DTOs: `PlayerStatsResponse`, `HeadToHeadResponse`, `PlatformSummaryResponse`, `OpeningStatsEntry`, `ErrorResponse`
  - Entidades: `GameRecord`, `PlayerStatsMV`, `ProcessedEvent`
  - `messaging/`: `ChessEvent`, `GameEventsConsumer`, `EtlEventsConsumer`
  - Repositorios: `GameRecordRepository`, `PlayerStatsMVRepository`, `ProcessedEventRepository`
  - `service/AnalyticsService`, `exception/GlobalExceptionHandler`, `exception/ApiException`
- Flyway V1-V3: `player_stats_mv`, `game_record`, `processed_event`.

### Tareas
1. **Recuperar fuentes** decompilando `target/classes/*.class` con CFR o Vineflower (`java -jar cfr.jar target/classes -d src/main/java`). Revisar manualmente cada archivo — lombok `@Data/@Builder` se expande, hay que restaurar anotaciones.
2. Crear `ms-analytics/pom.xml` copiando el de `ms-game/pom.xml` y ajustando: `artifactId=ms-analytics`, puerto 8084, sin dependencia AWS S3, sin Resilience4j salvo que el controller lo use.
3. Crear `ms-analytics/Dockerfile` (multi-stage igual a `ms-game/Dockerfile`).
4. Verificar que `src/main/resources/application.yml` y `application-docker.yml` existan. Si solo están en `target/classes`, copiarlos a `src/main/resources/`.
5. Mover migraciones `db/migration/V1..V3` de `target/classes/db/migration/` a `src/main/resources/db/migration/`.
6. Compilar con `JAVA_HOME=...ms-21.0.8 mvn -f ms-analytics/pom.xml clean package`.
7. Levantar en `docker-compose` y verificar consumo de `game.events` y `etl.events`.

### Endpoints esperados (según DTOs)
- `GET /analytics/players/{id}/stats` → `PlayerStatsResponse`
- `GET /analytics/players/{a}/vs/{b}` → `HeadToHeadResponse`
- `GET /analytics/platform/summary` → `PlatformSummaryResponse`
- Posible `GET /analytics/openings` → `List<OpeningStatsEntry>`

### Criterio de aceptación
- `mvn -f ms-analytics/pom.xml clean package` exitoso.
- `docker-compose up ms-analytics` healthy.
- `curl localhost:8084/analytics/platform/summary` → 200.
- Consumer procesa `game.finished` y actualiza `PLAYER_STATS_MV`.

---

## Brecha 2 — MS-Notifications: verificación funcional

### Estado actual
- Tiene `src/main/java/` con consumers (`UserEventConsumer`, `TournamentEventConsumer`, `GameEventConsumer`, `EtlEventConsumer`), `NotificationService`, `EmailService`, entidades `NotificationLog` y `ProcessedEvent`, `HealthController`, `RabbitConfig`, Flyway V1-V2.
- Reporte inicial decía "vacío"; es incorrecto. El código existe.

### Tareas
1. `mvn -f ms-notifications/pom.xml clean test` — confirmar compilación y cobertura.
2. Auditar cada consumer: asegurarse de que las queues declaradas coinciden con el binding de CONTEXT.md (`user.*`, `tournament.*`, `player.*`, `game.*`/`elo.*`, `etl.*`/`rating.*`).
3. Verificar que `NotificationService` escribe en `notification_log` con idempotencia via `processed_event` (usar `eventId` como clave).
4. `EmailService` debe ser no-op / log-only en profile `dev` (no enviar correos reales). Confirmar.
5. Añadir test de integración que publique un `game.finished` a RabbitMQ y verifique una fila nueva en `notification_log`.

### Criterio de aceptación
- Al registrar un jugador → fila en `notification_log` con `type=USER_WELCOME`.
- Al finalizar partida → fila con `type=GAME_RESULT` por cada participante.
- Eventos duplicados (mismo `eventId`) no generan filas duplicadas.

---

## Brecha 3 — Frontend: `organizer-panel` y `admin-panel` faltantes

### Estado actual
- `frontend/apps/` solo contiene `chess-portal`.
- `frontend/packages/` tiene `ui-lib` y `shared` (reutilizables).
- Puertos requeridos: organizer-panel 5174, admin-panel 5175.

### Tareas

**3.1 — organizer-panel (puerto 5174)**
- Scaffold con Vite + React + TS en `frontend/apps/organizer-panel/`.
- Reusar `@chessquery/ui-lib` y `@chessquery/shared` via workspace.
- Auth guard: solo rol `ORGANIZER`. Llama a BFF-Organizer (3002).
- Vistas mínimas:
  - Login
  - Dashboard (listado de mis torneos)
  - Crear torneo (formato, cupos, fechas, ELO min/max)
  - Detalle torneo → inscripciones, rondas, standings
  - Generar ronda / registrar resultados

**3.2 — admin-panel (puerto 5175)**
- Scaffold idéntico en `frontend/apps/admin-panel/`.
- Auth guard: rol `ADMIN`. Llama a BFF-Admin (3003).
- Vistas mínimas:
  - Dashboard (métricas globales desde MS-Analytics)
  - Listado de usuarios (ban/unban)
  - Listado de torneos (ver todos)
  - Panel de ETL: triggers de sync FIDE/Lichess

**3.3 — Integración Nginx**
- Agregar upstreams en `infrastructure/nginx/nginx.conf` para `organizer.localhost` y `admin.localhost` (o paths `/organizer`, `/admin`).

### Criterio de aceptación
- `npm run dev` en cada app levanta sin errores en puertos 5174/5175.
- Flujo E2E: login ORGANIZER → crear torneo → aparece en chess-portal.
- Login ADMIN → ver summary de MS-Analytics.

---

## Brecha 4 — `docker-compose.yml` campo `version` obsoleto

### Tarea
- Quitar la línea `version: "3.9"` en `infrastructure/docker-compose.yml`. Compose v2 la ignora y emite warning.
- Verificar `make up` sigue funcionando.

### Criterio de aceptación
- `docker compose config` sin warnings sobre `version`.

---

## Brecha 5 — Validación end-to-end pre-demo

### Tareas
1. Script `infrastructure/scripts/smoke-test.sh`:
   - Registrar usuario en MS-Auth.
   - Crear torneo en MS-Tournament (con header ORGANIZER).
   - Inscribir 4 jugadores.
   - Generar ronda 1.
   - Registrar 2 resultados en MS-Game.
   - Verificar `PLAYER_STATS_MV` actualizado en MS-Analytics.
   - Verificar filas en `notification_log`.
2. Checklist manual de puertos 5173/5174/5175 accesibles.

---

## Orden de ejecución sugerido

1. Brecha 4 (5 min, trivial).
2. Brecha 1 (MS-Analytics) — bloqueante para panel admin.
3. Brecha 2 (MS-Notifications verificación) — en paralelo con #1.
4. Brecha 3 (paneles frontend) — depende de #1 para admin-panel.
5. Brecha 5 (smoke test) — al final.

## Ramas

- `fix/compose-version-cleanup`
- `feat/ms-analytics-sources`
- `chore/ms-notifications-audit`
- `feat/organizer-panel`
- `feat/admin-panel`
- `test/e2e-smoke`

PRs individuales contra `main`, sin `Co-Authored-By`.
