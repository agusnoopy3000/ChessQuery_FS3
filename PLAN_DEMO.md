# PLAN DEMO — ChessQuery FS3

Fecha: 2026-04-23
Objetivo: dejar el sistema estable con endpoints demo-críticos cubiertos y monitoreo básico, para que la última semana sea solo correcciones y ensayo.

---

## 1. Endpoints críticos para la demo (guion narrativo)

Los endpoints se agrupan por rol/escena. Si una escena no funciona end-to-end, esa parte del demo se cae.

### Escena A — Registro y login (PLAYER)
**Flujo UI:** chess-portal `/register` → `/login` → `/portal`

| Endpoint | Servicio | Estado |
|---|---|---|
| `POST /auth/register` | ms-auth | ✅ |
| `POST /auth/login` | ms-auth | ✅ |
| `GET /auth/validate` | ms-auth (gateway) | ✅ |
| `GET /player/me/dashboard` | bff-player | ✅ |

**Riesgos:** que `user.registered` no llegue a ms-users → perfil vacío. Verificar binding `user.*` en ms-users.

### Escena B — Búsqueda y ranking
**Flujo UI:** `/search`, `/rankings`

| Endpoint | Servicio | Estado |
|---|---|---|
| `GET /users/search?q=` | ms-users | ✅ |
| `GET /users/ranking` | ms-users | ✅ |
| `GET /player/search` | bff-player | ✅ |
| `GET /player/rankings` | bff-player | ✅ |

**Riesgos:** `pg_trgm` no activado → 500. Validar seed de 10 jugadores chilenos cargada.

### Escena C — Torneo end-to-end (ORGANIZER)
**Flujo UI:** `/organizer/tournaments` → crear → inscribir → generar ronda → registrar resultado → standings

| Endpoint | Servicio | Estado |
|---|---|---|
| `POST /organizer/tournaments` | bff-organizer → ms-tournament | ✅ |
| `POST /organizer/tournaments/:id/join` (x N) | bff-organizer | ✅ |
| `POST /organizer/tournaments/:id/rounds/1/generate` | bff-organizer | ✅ |
| `PATCH /organizer/pairings/:pid/result` | bff-organizer | ✅ |
| `GET /organizer/tournaments/:id/standings` | bff-organizer | ✅ |

**Riesgos:** circuit breaker MS-Tournament → MS-Users puede caer en fallback (ELO 1500) si ms-users demora. Validar antes del demo.

### Escena D — Partida y PGN (MS-Game)
**Flujo:** registrar resultado dispara `POST /games`, apertura detectada, PGN subido a MinIO.

| Endpoint | Servicio | Estado |
|---|---|---|
| `POST /games` | ms-game | ✅ |
| `GET /games/{id}/pgn-url` | ms-game | ✅ |
| `GET /games?playerId=` | ms-game | ✅ |

**Riesgos:** MinIO bucket sin crear → 500 al subir PGN. Verificar script de init.

### Escena E — Analytics y admin (ADMIN)
**Flujo UI:** `/admin`, `/admin/etl`

| Endpoint | Servicio | Estado |
|---|---|---|
| `GET /admin/dashboard` | bff-admin → ms-analytics | ⚠️ ms-analytics sin `src/` |
| `GET /admin/etl/status` | bff-admin → ms-etl | ✅ |
| `POST /admin/etl/sync/:source` | bff-admin → ms-etl | ✅ |
| `GET /analytics/players/{id}/stats` | ms-analytics | ⚠️ solo `.class` |
| `GET /analytics/platform/summary` | ms-analytics | ⚠️ solo `.class` |

**Bloqueante:** recuperar fuentes de MS-Analytics (ver `SPEC_BRECHAS.md` brecha 1).

### Escena F — Notificaciones (background)
Demostrable vía RabbitMQ Management UI + consulta a `notification_log`.

| Acción | Verificación | Estado |
|---|---|---|
| Registrar usuario | fila `USER_WELCOME` | ⚠️ auditar |
| Finalizar partida | fila `GAME_RESULT` x2 | ⚠️ auditar |

---

## 2. Endpoints faltantes / a crear

Ordenados por prioridad demo:

1. **MS-Analytics** — todos sus endpoints (ver brecha 1). Bloquea escena E.
2. **MS-Notifications** — `GET /notifications?userId=` para mostrar log en UI admin (opcional, pero vistoso).
3. **MS-Users** — `GET /users/{id}/elo` existe interno; validar que BFF lo exponga si Escena B lo requiere.
4. **Gateway** — agregar ruta `/api/analytics/**` si se expone directamente al admin-panel.

**No crear** endpoints nuevos fuera de esta lista antes de la demo.

---

## 3. Monitoreo de rendimiento — plan mínimo viable

### Estado actual
- Actuator solo en api-gateway y ms-notifications.
- Sin Prometheus, Grafana, tracing, logs estructurados.

### Paso 1 — Actuator en todos los servicios Java (1 PR, ~1h)
Agregar a cada `pom.xml` de `ms-auth`, `ms-users`, `ms-tournament`, `ms-game`, `ms-analytics`, `ms-notifications`:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

En cada `application.yml`:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    tags:
      application: ${spring.application.name}
```

### Paso 2 — Stack Prometheus + Grafana en docker-compose (1 PR, ~2h)
Añadir a `infrastructure/docker-compose.yml`:

```yaml
prometheus:
  image: prom/prometheus:v2.54.1
  volumes:
    - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
  ports: ["9090:9090"]   # conflicto con ms-auth → usar 9091
grafana:
  image: grafana/grafana:11.2.0
  ports: ["3000:3000"]
  environment:
    GF_SECURITY_ADMIN_PASSWORD: admin
  volumes:
    - grafana-data:/var/lib/grafana
```

**Nota:** ms-auth ya usa puerto 9090 → exponer Prometheus en **9091** para evitar colisión.

Crear `infrastructure/prometheus/prometheus.yml` con scrape jobs para los 6 microservicios Java + api-gateway (path `/actuator/prometheus`).

### Paso 3 — Dashboard Grafana (1 PR, ~1h)
Importar dashboard oficial **Spring Boot 2.1 System Monitor** (ID 11378) o **JVM Micrometer** (ID 4701). Cubre:
- JVM heap / GC
- HTTP request rate, p50/p95/p99 latency por endpoint
- Threads, CPU
- Rate de errores 5xx

Panel adicional manual:
- Mensajes RabbitMQ consumidos/segundo (`rabbitmq_queue_messages_published_total` via exporter opcional — si no da tiempo, usar Management UI).

### Paso 4 — Healthchecks en docker-compose (30 min)
Cambiar healthchecks existentes para apuntar a `/actuator/health` en los servicios Java (hoy algunos usan `curl /` que devuelve 404).

### Paso 5 — Logs (opcional, solo si sobra tiempo)
- `logback-spring.xml` con formato JSON en cada servicio → facilita `docker compose logs | jq`.
- No instalar ELK — demasiado pesado para la demo.

### Qué medir durante la demo
Métricas a proyectar en pantalla secundaria:
1. **Request rate** por BFF (gráfico de líneas, últimos 5 min).
2. **Latencia p95** por microservicio (gate <300ms).
3. **JVM heap** por servicio (detectar leaks).
4. **RabbitMQ queue depth** (detectar consumers caídos).

---

## 4. Cronograma sugerido (hasta presentación)

Asumiendo presentación en ~2 semanas:

| Semana | Foco |
|---|---|
| **Semana 1 días 1-2** | Brecha 4 (compose version), actuator en todos los servicios (Paso 1) |
| **Semana 1 días 3-4** | Brecha 1 (recuperar MS-Analytics), Prometheus+Grafana (Paso 2-3) |
| **Semana 1 días 5-7** | Brecha 2 (auditar notifications), Brecha 3 start (organizer-panel o consolidar chess-portal) |
| **Semana 2 días 1-2** | Smoke test E2E, ajuste de healthchecks (Paso 4) |
| **Semana 2 días 3-4** | Congelar código. Solo bug-fixes |
| **Semana 2 días 5-6** | Ensayos completos de demo con monitoreo proyectado |
| **Día de la demo** | No tocar código. Solo `make up` + ensayo final |

---

## 5. Checklist pre-demo (día D)

- [ ] `make reset && make up` levanta todo healthy en <60s
- [ ] MinIO bucket `chessquery-pgn` existe
- [ ] RabbitMQ exchange `ChessEvents` y queues bindeadas
- [ ] Seed de 10 jugadores chilenos cargada en user_db
- [ ] Seed de 90+ aperturas en game_db
- [ ] Grafana en `localhost:3000` con dashboard cargado
- [ ] Prometheus en `localhost:9091` scrapeando los 7 servicios
- [ ] chess-portal en 5173 sirve login PLAYER/ORGANIZER/ADMIN
- [ ] Smoke test `infrastructure/scripts/smoke-test.sh` pasa
- [ ] Usuario demo creado con cada rol (credenciales anotadas)
- [ ] Torneo demo pre-cargado (opcional, agiliza la presentación)

---

## 6. Regla de oro

**Desde T-5 días a la presentación: congelar main.** Solo bugfixes con PR y smoke test. Ningún feature nuevo.
