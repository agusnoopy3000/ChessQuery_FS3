# ChessQuery — Contexto compartido para agentes de código

## Proyecto
Plataforma de microservicios para ajedrez competitivo en Chile.
Curso DSY1106 Desarrollo Fullstack III, DuocUC.
Equipo: Martín Mora, Agustín Garrido, Gabriel Espinoza.

## Convenciones generales

### Naming
- Java: camelCase para campos, PascalCase para clases. Paquete base: cl.chessquery.{servicio}
- TypeScript (BFF): camelCase para todo.
- Python (ETL): snake_case para campos y funciones, PascalCase para clases.
- Base de datos: snake_case para tablas y columnas (player_id, elo_national).
- JSON en APIs REST: camelCase siempre (playerId, eloNational). Los DTOs de Java mapean de snake_case de BD a camelCase de JSON.

### Formato de respuesta REST
Todos los endpoints exitosos retornan:
- POST: 201 Created con body del recurso creado
- GET: 200 OK con body
- PUT/PATCH: 200 OK con body actualizado
- DELETE: 204 No Content

Errores retornan:
{
  "status": 400|401|404|409|500,
  "error": "DESCRIPCION_CORTA",
  "message": "Descripción legible del error",
  "timestamp": "2026-04-14T10:30:00Z"
}

### Paginación
GET con listas usa query params: ?page=0&size=20
Respuesta paginada:
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8
}

### Autenticación
JWT con claims: sub (userId como string), email, role (PLAYER|ORGANIZER|ADMIN).
El API Gateway agrega headers downstream: X-User-Id, X-User-Email, X-User-Role.
Los microservicios y BFFs NO validan JWT ellos mismos, confían en el Gateway.

## Contratos de eventos RabbitMQ

Exchange: ChessEvents (tipo Topic)
Todos los payloads son JSON con estos campos exactos:

### user.registered
```json
{
  "eventId": "uuid-v4",
  "eventType": "user.registered",
  "timestamp": "2026-04-14T10:30:00Z",
  "payload": {
    "userId": 12,
    "email": "jugador@email.com",
    "firstName": "Cristóbal",
    "lastName": "Henríquez",
    "role": "PLAYER"
  }
}
```

### user.updated
```json
{
  "eventId": "uuid-v4",
  "eventType": "user.updated",
  "timestamp": "2026-04-14T10:30:00Z",
  "payload": {
    "userId": 12,
    "fieldsChanged": ["firstName", "clubId"]
  }
}
```

### tournament.created
```json
{
  "eventId": "uuid-v4",
  "eventType": "tournament.created",
  "timestamp": "2026-04-14T10:30:00Z",
  "payload": {
    "tournamentId": 5,
    "name": "Torneo Abierto Club Santiago",
    "format": "SWISS",
    "startDate": "2026-05-01",
    "maxPlayers": 32,
    "organizerId": 3
  }
}
```

### player.registered
```json
{
  "eventId": "uuid-v4",
  "eventType": "player.registered",
  "timestamp": "2026-04-14T10:30:00Z",
  "payload": {
    "playerId": 12,
    "tournamentId": 5,
    "seedRating": 1750,
    "registeredAt": "2026-04-14T10:30:00Z"
  }
}
```

### tournament.round.starting
```json
{
  "eventId": "uuid-v4",
  "eventType": "tournament.round.starting",
  "timestamp": "2026-04-14T10:30:00Z",
  "payload": {
    "tournamentId": 5,
    "roundNumber": 3,
    "startTime": "2026-05-01T15:00:00Z",
    "pairingsCount": 16
  }
}
```

### game.finished
```json
{
  "eventId": "uuid-v4",
  "eventType": "game.finished",
  "timestamp": "2026-04-14T10:30:00Z",
  "payload": {
    "gameId": 4521,
    "whitePlayerId": 12,
    "blackPlayerId": 34,
    "result": "1-0",
    "gameType": "TOURNAMENT",
    "openingId": 15,
    "totalMoves": 42,
    "tournamentPairingId": 89
  }
}
```

### elo.updated
```json
{
  "eventId": "uuid-v4",
  "eventType": "elo.updated",
  "timestamp": "2026-04-14T10:30:00Z",
  "payload": {
    "playerId": 12,
    "oldElo": 1750,
    "newElo": 1762,
    "delta": 12,
    "ratingType": "FIDE_STANDARD",
    "gameId": 4521
  }
}
```

### rating.updated
```json
{
  "eventId": "uuid-v4",
  "eventType": "rating.updated",
  "timestamp": "2026-04-14T10:30:00Z",
  "payload": {
    "source": "FIDE",
    "playersUpdated": 847,
    "ratingType": "FIDE_STANDARD",
    "syncId": 23
  }
}
```

### sync.completed
```json
{
  "eventId": "uuid-v4",
  "eventType": "sync.completed",
  "timestamp": "2026-04-14T10:30:00Z",
  "payload": {
    "source": "FIDE",
    "status": "SUCCESS",
    "recordsProcessed": 847,
    "recordsFailed": 3,
    "durationMs": 12500,
    "circuitBreakerState": "CLOSED"
  }
}
```

## Contratos REST inter-servicio

Los microservicios se llaman entre sí via HTTP. Estos son los endpoints que otros servicios consumen:

### MS-Users (consumido por MS-Tournament y BFFs)
- GET http://ms-users:8081/users/{id}/profile → retorna Player completo con country y club
- GET http://ms-users:8081/users/search?q={query}&limit=20 → lista de jugadores
- PUT http://ms-users:8081/users/{id}/elo → body: {ratingType, newValue, source}

### MS-Auth (consumido por API Gateway)
- GET http://ms-auth:9090/auth/validate → header Authorization: Bearer {jwt} → retorna {userId, email, role}

### MS-Tournament (consumido por BFF-Organizer)
- Todos los endpoints de tournament con prefijo http://ms-tournament:8082/

### MS-Game (consumido por BFF-Player)
- Todos los endpoints de game con prefijo http://ms-game:8083/

### MS-Analytics (consumido por BFF-Player y BFF-Admin)
- Todos los endpoints de analytics con prefijo http://ms-analytics:8084/

### MS-ETL (consumido por BFF-Admin)
- Todos los endpoints de etl con prefijo http://ms-etl:8086/

## URLs internas Docker
Dentro de Docker Compose, los servicios se comunican por nombre de servicio:
- ms-auth:9090, ms-users:8081, ms-tournament:8082, ms-game:8083
- ms-analytics:8084, ms-notifications:8085, ms-etl:8086
- bff-player:3001, bff-organizer:3002, bff-admin:3003
- rabbitmq:5672, redis:6379, minio:9000

## Modelo de datos (v3.1 — 3FN)

Documento visual: ChessQuery_ERD_UML_v3.1.html (en raíz del proyecto).
15 entidades · 7 bases de datos · 16 relaciones · Normalizado en 3FN.

### Entidades por microservicio

**auth_db** (MS-Auth)
- AUTH_USER: id, email UK, password_hash, role CK(PLAYER|ORGANIZER|ADMIN), is_active, last_login, created_at
- REFRESH_TOKEN: id, user_id FK→AUTH_USER, token_hash UK, expires_at, is_revoked, device_info, created_at

**user_db** (MS-Users)
- PLAYER: id, first_name NN, last_name NN, rut UK VARCHAR(12) nullable (jugadores extranjeros), email UK, country_id FK→COUNTRY, region, club_id FK→CLUB, birth_date, gender CK(M/F/O), fide_id UK, federation_id UK, lichess_username UK, elo_national, elo_fide_standard, elo_fide_rapid, elo_fide_blitz, elo_platform, created_at, updated_at
- COUNTRY: id, iso_code UK CHAR(3), name NN, fide_federation
- CLUB: id, name NN, country_id FK→COUNTRY, city, federation_code
- RATING_HISTORY: id, player_id FK→PLAYER, rating_type CK, rating_value NN, rating_prev_value, delta (desnorm. documentada), recorded_at NN, source
- PLAYER_TITLE_HISTORY: id, player_id FK→PLAYER, title CK, title_date NN, is_current DEFAULT TRUE, source

**tournament_db** (MS-Tournament)
- TOURNAMENT: id, name NN, description, format CK(SWISS|ROUND_ROBIN|KNOCKOUT), status CK, start_date NN, end_date, location, max_players, current_players COMPUTED, rounds_total NN, organizer_id (cross-svc bigint), min_elo, max_elo, time_control, created_at
- TOURNAMENT_REGISTRATION: id, tournament_id FK, player_id (cross-svc), UK(tournament_id, player_id), status CK, registered_at, seed_rating
- TOURNAMENT_ROUND: id, tournament_id FK, UK(tournament_id, round_number), round_number NN, round_date, status CK
- TOURNAMENT_PAIRING: id, round_id FK, white_player_id (cross-svc), black_player_id (cross-svc), result CK, board_number, CHECK(white_player_id ≠ black_player_id)

**game_db** (MS-Game)
- OPENING: id, eco_code UK VARCHAR(10), name NN, variation, pgn_moves TEXT
- GAME: id, white_player_id (cross-svc), black_player_id (cross-svc), result CK, game_type CK, white_elo_before, black_elo_before, white_elo_after, black_elo_after, total_moves, opening_id FK→OPENING nullable, pgn_storage_key VARCHAR(500) NN, tournament_pairing_id nullable (cross-svc), duration_seconds, played_at NN, created_at

**analytics_db** (MS-Analytics)
- PLAYER_STATS_MV: player_id PK, total_games, wins, losses, draws, win_rate DECIMAL(5,2), avg_moves, current_streak, best_elo, last_refreshed. Vista materializada actualizada por eventos RabbitMQ.

**etl_db** (MS-ETL)
- ETL_SYNC_LOG: id, source CK, started_at NN, finished_at, status CK, records_processed DEFAULT 0, records_failed DEFAULT 0, error_message, cb_state

**notif_db** (MS-Notifications)
- NOTIFICATION_LOG: id, recipient_id NN, channel CK, event_type NN, status CK, subject, payload JSONB, sent_at, retry_count DEFAULT 0, created_at

### Desnormalizaciones documentadas
- PLAYER.elo_* (5 campos): snapshots del último valor para consultas rápidas de perfil/ranking sin JOIN. El historial vive en RATING_HISTORY.
- RATING_HISTORY.delta y rating_prev_value: derivados calculados al insertar, para consultas analíticas de variación sin self-JOIN.
- PLAYER_STATS_MV: tabla completa de agregados pre-calculados, mantenida por eventos, evita lecturas cross-DB.

### Notas de implementación
- rut: almacenado como "12345678-9" (con guion, sin puntos). Nullable para extranjeros.
- player_id en TOURNAMENT y GAME es BIGINT cross-service (no FK real de BD); la integridad se garantiza a nivel de aplicación.
- organizer_id en TOURNAMENT es cross-service (referencia a AUTH_USER.id o PLAYER.id según rol).

---

## Algoritmo ELO

Fórmula FIDE estándar (no FECHAJ chilena). Decisión: compatibilidad con datos FIDE/Lichess/AJEFECH.

```
E = 1 / (1 + 10^((Rb - Ra) / 400))
R' = R + K × (S − E)
```

- S: 1.0 victoria, 0.5 tablas, 0.0 derrota
- K=32: jugadores con `totalGames < 30` (según PLAYER_STATS_MV)
- K=16: jugadores con `totalGames >= 30`
- Sin K=40 ni K=20. Simplificación deliberada para contexto académico (Factory Method con dos valores).

**Flujo de cálculo:** MS-Game calcula ELO de ambos jugadores al registrar resultado → persiste en `white_elo_after` / `black_elo_after` en GAME → publica dos eventos `elo.updated` (uno por jugador).

**Obtener totalGames:** MS-Game consulta MS-Analytics vía REST con Circuit Breaker (Resilience4j). Fallback: K=32 (asume jugador nuevo, caso más generoso).

---

## Formatos de torneo y patrón Factory Method

Valores del campo `format` en TOURNAMENT: `SWISS`, `ROUND_ROBIN`, `KNOCKOUT`.

**Interfaz:** `PairingStrategy` con método `generatePairings(List<PlayerStanding>, int roundNumber)`.

| Formato | Implementación | Descripción |
|---|---|---|
| SWISS | SwissPairingStrategy | Empareja por puntos, evita repetición. Desempate: Buchholz + Sonneborn-Berger. Rondas: 5-9 para 16-64 jugadores. |
| ROUND_ROBIN | RoundRobinPairingStrategy | Rotación Berger, todos vs todos. Rondas: N-1. Torneos pequeños 8-12 jugadores. |
| KNOCKOUT | KnockoutPairingStrategy | Eliminación directa. Requiere potencia de 2 (8/16/32/64 jugadores). Seed por rating descendente. |

**Factory:** `PairingStrategyFactory.getStrategy(TournamentFormat) → PairingStrategy`

---

## Object Storage — PGN en MinIO/S3

- Formato: PGN raw (texto plano, sin parsear), extensión `.pgn`
- Key en MinIO: `games/{year}/{month}/{gameId}.pgn` → ej: `games/2026/04/4521.pgn`
- Campo en BD: `GAME.pgn_storage_key` almacena exactamente esa key
- Content-Type: `application/x-chess-pgn`
- Tamaño máximo: 1MB por archivo (una partida normal pesa 2KB-50KB)
- Acceso: MS-Game genera presigned URL con expiración de 1 hora. El cliente descarga directo de MinIO/S3, sin pasar por el backend.
- Detección de apertura: MS-Game lee los primeros 10 movimientos del PGN antes de subirlo → busca coincidencia en OPENING comparando prefijo contra `pgn_moves` → si no hay match, `opening_id = null`.

---

## Autenticación — Flujo completo con refresh tokens

**Access token (JWT)**
- Expiración: 1 hora
- Claims: `sub` (userId string), `email`, `role`, `iat`, `exp`
- Header: `Authorization: Bearer {token}`
- Stateless: no se persiste en BD
- El API Gateway lo valida en cada request llamando a `GET /auth/validate` en MS-Auth

**Refresh token**
- Expiración: 7 días
- Generación: UUID v4 hasheado con SHA-256 → persistido en REFRESH_TOKEN.token_hash
- Un usuario puede tener múltiples refresh tokens activos (uno por dispositivo)
- `device_info`: user-agent o identificador enviado por el cliente

**Flujo login:**
1. POST /auth/login → MS-Auth valida credenciales → genera access token (1h) + refresh token (7d)
2. Persiste hash del refresh token en REFRESH_TOKEN
3. Retorna ambos tokens al cliente
4. Cliente guarda: access token en memoria JS, refresh token en localStorage

**Flujo refresh:**
- Al recibir 401, cliente llama POST /auth/refresh con el refresh token
- MS-Auth valida que no esté expirado ni revocado → emite nuevo access token
- Si el refresh también expiró/revocado → 401 → cliente redirige a login

**Flujo logout:**
- POST /auth/logout con el refresh token
- MS-Auth setea `is_revoked = true` en REFRESH_TOKEN
- El access token sigue válido hasta expirar (máx 1h), cliente lo descarta del frontend

---

## Rate Limiting (API Gateway)

Implementación: Spring Cloud Gateway + RedisRateLimiter (`RequestRateLimiterGatewayFilterFactory`).

| Scope | Límite | Endpoints |
|---|---|---|
| Autenticados (por IP) | 100 req/min (burst 120) | `/api/*` |
| Auth endpoints (por IP) | 20 req/min | `/auth/login`, `/auth/register` |
| Validate (interno) | Sin límite | `/auth/validate` (no expuesto) |

Configuración: `replenishRate=100`, `burstCapacity=120`, `requestedTokens=1`. Key resolver: IP remota.
Al exceder: `HTTP 429 Too Many Requests` con header `Retry-After` (segundos a esperar).

---

## Datos de prueba para la demo

Orden de seed respetando dependencias de FK:
1. auth_db → 2. user_db (COUNTRY, CLUB, PLAYER) → 3. tournament_db → 4. game_db (OPENING, GAME) → 5. analytics_db

Datos requeridos:
- 3 usuarios auth: admin@chessquery.cl (ADMIN), organizador@chessquery.cl (ORGANIZER), jugador@chessquery.cl (PLAYER). Password para todos: "Chess2026!"
- 10 jugadores chilenos con nombres realistas, ratings variados (1200-2200), clubes de Santiago, Valparaíso, Concepción.
- 2 torneos: uno en estado OPEN con 5 inscritos, otro FINISHED con resultados completos.
- 20 partidas de ejemplo con resultados y aperturas variadas.
- 30 aperturas ECO populares en la tabla OPENING.
