# ChessQuery — Documento de implementación

> Plataforma de microservicios para ajedrez competitivo en Chile.
> Curso DSY1106, DuocUC. Equipo: Martín Mora, Agustín Garrido, Gabriel Espinoza.
> Última actualización: 2026-05-09.

---

## 1. Arquitectura general

```
┌────────────────────────────────────────────────────────────────────────┐
│                          Frontends (Vite)                              │
│  • chess-portal (5173)        → PLAYER                                 │
│  • organizer-panel (5174)     → ORGANIZER                              │
│  • admin-panel (futuro)       → ADMIN                                  │
└────────────────────────────────────────────────────────────────────────┘
                              │ HTTPS + JWT (Bearer)
                              ▼
┌────────────────────────────────────────────────────────────────────────┐
│                  API Gateway · Spring Cloud Gateway (8080)             │
│  • SupabaseJwtAuthFilter (validación JWT local: ES256/JWKS + HS256)    │
│  • PlayerIdResolver (UUID → player.id, cache Caffeine)                 │
│  • Auto-provisioning de Player ante 404                                │
│  • RateLimiter (Redis, 100 rpm/IP)                                     │
│  • CORS, route mapping → BFFs                                          │
│  • Webhook receiver: /webhooks/supabase/user-registered                │
└────────────────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
┌────────────────┐  ┌──────────────────┐  ┌─────────────────┐
│  bff-player    │  │  bff-organizer   │  │  bff-admin      │
│   (3001)       │  │   (3002)         │  │   (3003)        │
│  NestJS BFF    │  │  NestJS BFF      │  │  NestJS BFF     │
└────────────────┘  └──────────────────┘  └─────────────────┘
        │                     │                     │
        └─────────────────────┼─────────────────────┘
                              ▼
┌────────────────────────────────────────────────────────────────────────┐
│                     Microservicios (Spring Boot 3.2)                   │
│  ms-users (8081)        Player profiles, ELO, búsqueda fuzzy           │
│  ms-tournament (8082)   Torneos, inscripciones, rondas, pairings       │
│  ms-game (8083)         Partidas live, ELO calc, PGN, openings         │
│  ms-analytics (8084)    Stats agregadas (PLAYER_STATS_MV)              │
│  ms-notifications (8085) Inbox in-app + email (mock) + toasts          │
│  ms-etl (8086)          Sync FIDE/Lichess (en progreso)                │
└────────────────────────────────────────────────────────────────────────┘
        │           │           │           │           │
        ▼           ▼           ▼           ▼           ▼
   ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐
   │user_db │  │tournm.t│  │game_db │  │analyt. │  │notif_db│
   │  5433  │  │  5434  │  │  5435  │  │  5436  │  │  5437  │
   └────────┘  └────────┘  └────────┘  └────────┘  └────────┘

      ┌──────────────────────────────────────────────────────┐
      │ Infra compartida                                     │
      │  • RabbitMQ (5672, 15672) — bus de eventos           │
      │  • Redis (6379) — rate limit + cache                 │
      │  • Supabase local (54321/54322/54323/54324)          │
      │    Auth · Postgres · Studio · Mailpit · Realtime     │
      │  • MinIO/Supabase Storage — PGN files                │
      └──────────────────────────────────────────────────────┘
```

**Decisiones clave:**
- Cada microservicio dueño absoluto de su DB (DB-per-service). FKs cross-DB son BIGINT planos sin integridad referencial.
- JWT validado solo en el gateway. Los MS confían en headers `X-User-Id`, `X-User-Role`, `X-User-Email`.
- Eventos vía RabbitMQ topic `ChessEvents`. Routing keys: `user.*`, `tournament.*`, `player.*`, `game.*`, `elo.*`, `registration.*`, `etl.*`.
- Realtime broadcast (live games + push notifs futuras) vía Supabase Realtime con service key desde el backend.

---

## 2. Identidad y autenticación

### 2.1 Componentes
- **Supabase Auth** es el origen único de identidad. Email + password + JWT ES256.
- **`auth.users.id`** (UUID) es el identificador inmutable.
- **`Player`** en `user_db` es el perfil rico (ELO, club, lichess, RUT, etc.) con FK lógica `supabase_user_id`.

### 2.2 Flujo de registro

```
1. Usuario completa Register.tsx (chess-portal)
       └──> supabase.auth.signUp({ email, password, data: {role, firstName, lastName, lichessUsername, clubName} })

2. Supabase Auth crea auth.user → emite JWT con sub=UUID, user_metadata embebido.

3. (En paralelo) Webhook DB de Supabase → POST /webhooks/supabase/user-registered (gateway)
       └──> Gateway valida X-Supabase-Webhook-Secret
       └──> Publica RabbitMQ user.registered { userId, email, firstName, lastName, lichessUsername }
       └──> ms-users.UserRegisteredConsumer → playerService.provisionBySupabaseId(...)
       └──> INSERT Player con supabase_user_id = UUID

4. Si el webhook falló o tarda, el siguiente request del usuario al gateway lo cubre:
       └──> SupabaseJwtAuthFilter → PlayerIdResolver.resolve(uuid, claims)
            ├──> GET /users/by-supabase-id/{uuid}
            └──> 404 → POST /users/provision (mismo método, idempotente) → 200 con player.id
       └──> Cachea uuid → player.id (TTL 5 min)
```

**Diagrama:**

```
chess-portal           Supabase           Gateway          ms-users         user_db
     │                    │                  │                │                │
     │  signUp(email...)  │                  │                │                │
     │ ─────────────────> │                  │                │                │
     │                    │   webhook        │                │                │
     │                    │ ───────────────> │                │                │
     │                    │                  │ user.registered│                │
     │                    │                  │ (Rabbit)       │                │
     │                    │                  │ ─────────────> │  INSERT Player │
     │                    │                  │                │ ─────────────> │
     │  JWT + session     │                  │                │                │
     │ <───────────────── │                  │                │                │
     │                                                                          │
     │  GET /api/player/me   (Bearer JWT)                                       │
     │ ───────────────────> │  validate JWT                                     │
     │                      │  resolve UUID  → 200 player.id                    │
     │                      │  (si 404 → POST /users/provision con claims)      │
```

### 2.3 Flujo de login

```
1. Login.tsx (chess-portal/organizer-panel) → supabase.auth.signInWithPassword(...)
2. Supabase devuelve session { access_token, refresh_token, user }.
3. Frontend almacena en sessionStorage (storageKey único por tab para evitar cross-tab pollution).
4. Cada request usa header Authorization: Bearer {access_token}.
5. Token expira → cliente Supabase auto-refresca usando refresh_token.
```

### 2.4 Auto-provisión (resiliencia)
Si el webhook nunca llegó (deshabilitado en local, fallo de red), el primer request autenticado del usuario al gateway dispara `POST /users/provision` con los claims del JWT (`email`, `firstName`, `lastName`, `lichessUsername`, `clubName` desde `user_metadata`). El método es idempotente: por UUID → no-op, por email → asocia, sino crea.

---

## 3. Autorización

### 3.1 Roles
- `PLAYER` — default; juega, se inscribe a torneos, ve standings.
- `ORGANIZER` — crea torneos, aprueba inscripciones, genera rondas, registra resultados.
- `ADMIN` — administra todo + acceso a panel admin.

El rol vive en `auth.users.user_metadata.role` (definido al signup) y se propaga al header `X-User-Role`.

### 3.2 Enforcement
- **Gateway** propaga rol pero no enforce.
- **Microservicios** validan rol vía `@RequestHeader("X-User-Role")` en endpoints sensibles. Ejemplo en `ms-tournament`:

```java
@PostMapping
public TournamentResponse create(@RequestHeader("X-User-Role") String role, ...) {
    if (!"ORGANIZER".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
        throw new ApiException(403, "FORBIDDEN", "Solo organizadores...");
    }
    ...
}
```

⚠️ **Limitación actual**: si un atacante alcanza directamente `http://host:8081/users/...` saltándose el gateway, los headers se pueden falsear. Ver `SECURITY_PLAN.md` P0.1 + P0.2.

---

## 4. Flujo de live game (1v1 en tiempo real)

### 4.1 Componentes
- `ms-game` — entidad `LiveGameSession` + `LiveGameMove`. Validación con `chesslib`.
- `LiveGameBroadcaster` — publish de eventos a Supabase Realtime via REST con service key.
- Frontend: `LiveGame.tsx` (chess-portal). Chessground para tablero. `chessops` para validación local + optimismo.

### 4.2 Secuencia

```
A (white)                    Backend (ms-game)              B (black)
   │                              │                             │
   │ POST /games/live (create)    │                             │
   │ ──────────────────────────>  │ INSERT session(WAITING)     │
   │ <──────────────── 201        │                             │
   │                              │                             │
   │                              │ POST /games/live/{id}/join  │
   │                              │ <─────────────────────────  │
   │                              │ UPDATE session(ACTIVE)      │
   │                              │ Realtime broadcast          │
   │                              │ "game.started" canal game:N │
   │ <────────────────────────────┴───────────────────────────> │
   │                                                            │
   │ click pieza → optimistic UI:                               │
   │   - chessops valida localmente                             │
   │   - setState({turn flipped, +move, +clock-incr})           │
   │   - chessground re-renderea                                │
   │ POST /games/live/{id}/move {uci, clocks}                   │
   │ ──────────────────────────>  validate + persist            │
   │                              UPDATE session(turn,fen,clk)  │
   │                              Realtime "move.played"        │
   │                              + estado completo en payload  │
   │ <──────────── 200 reconcile  ───────────────────────────>  │
   │                                                          B aplica state
```

**Detalles importantes:**
- **Optimistic UI total** en el cliente que mueve: turno + reloj + lista de jugadas se actualizan al instante sin esperar el backend (~50-200 ms).
- **Reconciliación** automática cuando llega la respuesta del POST.
- **Rollback** si el server rechaza (jugada ilegal, no es turno, etc.).
- **Pre-moves** (R9) — chessground reproduce automáticamente cuando el turno vuelve.
- **Reloj Fischer**: incremento se suma al lado que acaba de mover.
- **Detección de fin** (jaque mate, ahogado, repetición, 50 movimientos) en el server al aplicar la jugada.

### 4.3 Bug fix relevante (commit `085e0bf`)
Click-precision en bando blanco: chessground memoiza `getBoundingClientRect` del tablero al montar. Si el banner "Es tu turno" aparece después (al activarse el game), el offset de bounds queda stale → click registra ½ casilla off. Fix: invalidar `bounds.clear()` en `pointerdown` con capture-phase + `ResizeObserver` propio sobre el contenedor.

---

## 5. Flujo de torneos con aprobación

### 5.1 Estados
- **Tournament**: `DRAFT` → `OPEN` → `IN_PROGRESS` → `FINISHED`.
- **Registration**: `PENDING` → `CONFIRMED` (o `REJECTED`); `CONFIRMED`/`PENDING` → `CANCELLED`.
- Flag `Tournament.requiresApproval` (default `true`) controla si las inscripciones quedan en `PENDING` o pasan directo a `CONFIRMED`.

### 5.2 Secuencia end-to-end

```
ORGANIZER (Carla)                                PLAYER (Bruno)
       │                                                │
   1. Crear torneo (modal)                              │
   POST /api/organizer/tournaments                      │
   {name, format, requiresApproval=true, ...}           │
       │ → ms-tournament                                │
       │   INSERT tournament(DRAFT)                     │
       │   publish tournament.created                   │
       │                                                │
   2. Abrir inscripciones                               │
   PATCH /api/organizer/tournaments/{id}/status         │
   {newStatus:"OPEN"}                                   │
                                                        │
                                                  3. Ver torneos abiertos
                                                  GET /api/player/tournaments?status=OPEN
                                                        │
                                                  4. Inscribirse
                                                  POST /api/player/tournaments/{id}/register
                                                        │ → ms-tournament
                                                        │   validate (cupos, ELO range, no duplicado)
                                                        │   INSERT registration(PENDING, seedRating)
                                                        │   publish registration.pending
                                                        │
   5. ms-notifications consume registration.pending     │
      → notif IN_APP a Carla "Nueva inscripción..."     │
      → toast emergente en organizer-panel              │
                                                        │
   6. Carla ve la inscripción en RegistrationsPanel     │
      Botones [Aprobar] [Rechazar (con razón)]          │
                                                        │
   7. PATCH /api/organizer/registrations/{rid}/approve  │
      → ms-tournament                                   │
        UPDATE status=CONFIRMED                         │
        publish registration.approved                   │
        publish player.registered (compat)              │
                                                        │
   8. ms-notifications consume registration.approved    │
      → IN_APP + EMAIL a Bruno                          │
                                                  9. Toast emergente en chess-portal:
                                                     "¡Aprobado! Estás dentro de FinalCup"
                                                     Bell badge +1
                                                        │
  10. Cuando Carla suficientes jugadores:               │
      PATCH /tournaments/{id}/status {IN_PROGRESS}      │
      POST /tournaments/{id}/rounds/1                   │
      → genera pairings (Swiss/Round-Robin/Knockout)    │
      → publish tournament.round.starting
```

### 5.3 Endpoints relevantes
| Verbo | Path | Quién | Qué hace |
|---|---|---|---|
| POST | `/api/organizer/tournaments` | ORGANIZER | Crea torneo en DRAFT |
| PATCH | `/api/organizer/tournaments/{id}/status` | ORGANIZER | Transiciona estado con validaciones |
| GET | `/api/organizer/tournaments/{id}/registrations` | ORGANIZER | Lista enriquecida con nombres + ELO |
| PATCH | `/api/organizer/registrations/{rid}/approve` | ORGANIZER | PENDING → CONFIRMED |
| PATCH | `/api/organizer/registrations/{rid}/reject` | ORGANIZER | PENDING → REJECTED (con razón) |
| GET | `/api/player/tournaments?status=OPEN` | PLAYER | Listado público filtrable |
| GET | `/api/player/tournaments/{id}/my-registration` | PLAYER | Estado de mi inscripción |
| POST | `/api/player/tournaments/{id}/register` | PLAYER | Crea inscripción PENDING o CONFIRMED |

### 5.4 Pairing strategies (patrón Factory Method)
`PairingStrategyFactory.getStrategy(format)` retorna:
- `SwissPairingStrategy` — emparejamiento por puntos similares, evita revanchas, balance de colores.
- `RoundRobinPairingStrategy` — todos contra todos, algoritmo del círculo.
- `KnockoutPairingStrategy` — eliminación directa con bracket.

Standings con desempate FIDE: puntos → Buchholz → Sonneborn-Berger.

---

## 6. Sistema de notificaciones

### 6.1 Capas
- **Capa 1 (in-app)**: tabla `notification_log` en `notif_db` con `recipient_id`, `event_type`, `subject`, `payload`, `channel=IN_APP`, `read_at`.
- **Capa 2 (email)**: `MockEmailService` que loggea (en dev) o envía a Mailpit (54324). En prod: SMTP real o Resend/SendGrid.
- **Capa 3 (push toast)**: polling cada 8s desde el frontend, detecta nuevas (`id > lastSeenId`), muestra toast emergente auto-dismiss a 5s.

### 6.2 Eventos consumidos
| Routing key | Cola | Acción |
|---|---|---|
| `user.registered` | `user.events` | IN_APP welcome al jugador |
| `player.registered` | `tournament.events` | IN_APP "Estás inscrito en…" |
| `registration.pending` | `tournament.events` | IN_APP al ORGANIZER "Nueva inscripción" |
| `registration.approved` | `tournament.events` | IN_APP + EMAIL al jugador |
| `registration.rejected` | `tournament.events` | IN_APP + EMAIL al jugador (con razón) |
| `tournament.round.starting` | `tournament.events` | IN_APP a inscritos |
| `game.finished` | `game.events` | IN_APP + EMAIL "Tu partida terminó" |
| `elo.updated` | `game.events` | IN_APP "ELO: 1500 → 1516" |

### 6.3 Frontend `NotificationBell`
- Polling 8s a `/api/{player|organizer}/notifications/unread-count` + `/notifications`.
- Compara con `lastSeenIdRef`. Para cada `id > lastSeenId` empuja un toast.
- Stack de toasts top-right, slide-in, dismissable individual o por click.
- Bell con badge rojo si hay no leídas. Click abre dropdown que muestra historial y marca todas como leídas.

---

## 7. Eventos RabbitMQ — contrato

```json
{
  "eventId": "uuid-v4",
  "eventType": "tournament.created",
  "timestamp": "2026-05-09T12:34:56Z",
  "payload": {
    "tournamentId": 12,
    "name": "FinalCup",
    "organizerId": 978,
    "format": "SWISS"
  }
}
```

**Idempotencia**: cada consumer mantiene `processed_event(event_id PK)` y descarta duplicados.
**Acks manuales** en consumers críticos (notifications) para no perder eventos en errores transitorios.

---

## 8. Storage — PGN

- Bucket `chessquery-pgn` (Supabase Storage o MinIO).
- Path: `games/{year}/{month}/{gameId}.pgn`.
- Upload por `ms-game` con service key.
- Acceso por usuario via signed URL (TTL 1 h) servido por `GET /games/{id}/pgn-url`.
- Detección de apertura: primeros 10 movimientos del PGN matched contra tabla `opening` (90+ ECO codes seed).

---

## 9. ELO

- Fórmula FIDE estándar: `K=32` para `totalGames < 30`, `K=16` después.
- Calculado por `EloCalculator` en `ms-game` al finalizar partida.
- Publica `elo.updated` event → `ms-users` actualiza `Player.eloNational` via consumer dedicado (`users.elo.queue`).
- Ratings histórico en `rating_history` (cada cambio = una fila con `created_at`).

---

## 10. Stack y herramientas

| Capa | Tecnología |
|---|---|
| Frontend | React 18 + Vite + TypeScript + chessground + chessops + @tanstack/react-query + supabase-js |
| BFF | NestJS + axios |
| Microservicios | Java 17 + Spring Boot 3.2.4 + Spring Cloud 2023.0.0 + JPA/Hibernate + Lombok |
| Validación de ajedrez | bhlangonijr/chesslib (server) + chessops (client) |
| DB | PostgreSQL 16 (db-per-service) + Flyway migrations |
| Mensajería | RabbitMQ 3 (topic exchange `ChessEvents`) |
| Cache + rate limit | Redis 7 |
| Auth | Supabase Auth (JWT ES256 + JWKS) |
| Storage | Supabase Storage / MinIO (S3-compatible) |
| Realtime | Supabase Realtime (broadcast canales `game:{id}`) |
| Resiliencia | Resilience4j (circuit breaker, retry) |
| Observabilidad | Spring Boot Actuator + logs estructurados (futuro: Loki) |

---

## 11. Convenciones

- Java: `camelCase` campos, `PascalCase` clases, paquete `cl.chessquery.{servicio}`.
- TypeScript: `camelCase` everywhere, interfaces con `PascalCase`.
- SQL: `snake_case`.
- REST JSON: `camelCase` siempre.
- RUT formato `"12345678-9"` (con guión, sin puntos), nullable para extranjeros.
- Commits: Conventional Commits (`feat:`, `fix:`, `docs:`, `refactor:`, `perf:`).

---

## 12. Estado actual de implementación

| Componente | Estado |
|---|---|
| MS-Auth (legacy) | DEPRECATED — reemplazado por Supabase Auth |
| MS-Users | ✅ Production-ready |
| MS-Tournament | ✅ + flujo PENDING/approval (commit `085e0bf`) |
| MS-Game | ✅ + live games + optimistic UI |
| MS-Analytics | 🟡 Stats básicas, falta MV refresh scheduled |
| MS-Notifications | ✅ N1 (in-app) + N2 (email mock) + N3 (push toast) |
| MS-ETL | 🟡 Lichess sync básico, FIDE pendiente |
| API Gateway | ✅ JWT + auto-provision + rate limit |
| BFF Player | ✅ |
| BFF Organizer | ✅ + endpoints inscripciones + notifs |
| BFF Admin | 🟡 Endpoints básicos |
| chess-portal | ✅ |
| organizer-panel | ✅ + modal crear torneo + panel inscripciones + bell |
| admin-panel | 🔴 Pendiente |

---

## 13. Documentos relacionados

- `CONTEXT.md` — ERD completo, contratos detallados.
- `SECURITY_PLAN.md` — auditoría de seguridad y plan priorizado.
- `PLAN_DEMO.md` — guion de la demo.
- `HISTORIAS_USUARIO.md` — historias de usuario.
- `SPEC_LEY21719.md` — cumplimiento de Ley de Protección de Datos Chile.
- `SPEC_BRECHAS.md` — brechas conocidas.
- `docs/PLAN_MIGRACION_CLOUD.md` — migración a Supabase + AWS.

---

## 14. Quickstart desarrollo

```bash
# 1. Levantar Supabase local
supabase start

# 2. Levantar la infra completa
cd infrastructure
make up                     # ~1-2 min hasta healthchecks verdes
docker compose ps           # confirmar (healthy)

# 3. Levantar frontends
cd ../frontend/apps/chess-portal && npm run dev -- --host 0.0.0.0 --port 5173 &
cd ../organizer-panel && npm run dev -- --host 0.0.0.0 --port 5174 &

# 4. Credenciales demo (password: demo1234)
# - ana@demo.cl       (PLAYER, id=4488)
# - bruno@demo.cl     (PLAYER, id=977)
# - carla@demo.cl     (ORGANIZER, id=978)

# 5. URLs útiles
# http://localhost:5173    chess-portal
# http://localhost:5174    organizer-panel
# http://localhost:54323   Supabase Studio
# http://localhost:54324   Mailpit (emails)
# http://localhost:15672   RabbitMQ UI (chessquery / chessquery_dev)
# http://localhost:9001    MinIO console (minioadmin / minioadmin)
```

### Compilación Java

```bash
# Maven del sistema usa Java 25, pero Lombok 1.18.30 requiere ≤ 21.
export JAVA_HOME="/Users/agustingarridosnoopy/Library/Java/JavaVirtualMachines/ms-21.0.8/Contents/Home"
mvn -f ms-tournament/pom.xml clean test
```

### Reset demo

```bash
cd infrastructure
make demo-reset             # purga datos demo, preserva schemas
make clean-setup            # re-crea RabbitMQ + MinIO setup containers
```
