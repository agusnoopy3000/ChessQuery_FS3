# Catálogo de eventos — `ChessEvents`

> Fuente de verdad de la mensajería asíncrona de ChessQuery. Todo evento nuevo se
> agrega **primero aquí** (nombre, publicador, consumidores, payload, idempotencia)
> antes de codificar el productor/consumidor. Es el artefacto de sincronización del
> equipo (ver `CLAUDE.md` §5.1 del backlog de ejecución).

## Convenciones

- **Exchange:** `ChessEvents`, tipo `topic`, `durable=true`. Declarado (idempotente) por cada servicio que produce o consume.
- **Envelope común** de todo mensaje:
  ```json
  {
    "eventId":   "uuid-v4",
    "eventType": "<routing.key>",
    "timestamp": "ISO-8601 (Instant.now)",
    "payload":   { ...campos del evento... }
  }
  ```
- **Routing key = `eventType`**. El binding usa comodines topic (`game.*`, `elo.*`, …).
- **Publicación best-effort:** el publicador captura la excepción y loguea; un fallo de RabbitMQ **no** revierte la transacción de negocio. Idempotencia y reintentos viven en el consumidor.
- **Idempotencia:** los consumidores críticos (MS-Notifications) deduplican por `eventId` vía tabla `processed_event`. MS-Analytics reaplica de forma idempotente sobre `PLAYER_STATS_MV`.

## Topología de colas (estado actual del código)

| Servicio consumidor | Cola | Routing keys ligadas |
|---|---|---|
| MS-Analytics | `game.events` | `game.*` |
| MS-Analytics | `etl.events` | `etl.*`, `rating.*` |
| MS-Notifications | `user.events` | `user.*` |
| MS-Notifications | `tournament.events` | `tournament.*`, `player.*`, `registration.*` |
| MS-Notifications | `notif.game.events` | `elo.*`, `game.*` |
| MS-Notifications | `etl.events` | `etl.*` |
| MS-Users | `users.elo.queue` | `elo.*` |
| MS-Users | `users.rating.queue` | `rating.*` |
| MS-Tournament | `tournament.game.results.queue` | `game.finished` |

> Nota de diseño: MS-Users consume `elo.*`/`rating.*` en colas dedicadas (no comparte
> `game.events` con Analytics) para evitar el problema de *competing consumers*.

---

## Eventos vigentes (implementados en el código)

### Familia `user.*` — publica **MS-Users**

| Evento | Payload (campos) | Consumidores |
|---|---|---|
| `user.registered` | `playerId`, datos de perfil básicos | MS-Notifications (`user.events`) |
| `user.updated` | `playerId`, campos actualizados | MS-Notifications (`user.events`) |

### Familia `tournament.*` / `player.*` / `registration.*` — publica **MS-Tournament**

| Evento | Payload (campos) | Consumidores |
|---|---|---|
| `tournament.created` | `tournamentId`, `name`, `organizerId`, … | MS-Notifications (`tournament.events`) |
| `player.registered` | `tournamentId`, `playerId`, `seedRating`, `tournamentName?` | MS-Notifications (`tournament.events`) |
| `registration.pending` | `tournamentId`, `playerId`, … | MS-Notifications (`tournament.events`) |
| `registration.approved` | `tournamentId`, `playerId`, … | MS-Notifications (`tournament.events`) |
| `registration.rejected` | `tournamentId`, `tournamentName`, `playerId`, `reason` | MS-Notifications (`tournament.events`) |
| `tournament.round.starting` | `tournamentId`, `roundNumber`, … | MS-Notifications (`tournament.events`) |

### Familia `game.*` / `elo.*` — publica **MS-Game** (y `game.finished` re-emitido por MS-Tournament)

| Evento | Payload (campos) | Consumidores |
|---|---|---|
| `game.finished` | `gameId`, `whitePlayerId`, `blackPlayerId`, `result`, `gameType`, `whiteEloBefore/After`, `blackEloBefore/After`, `totalMoves`, `openingId?`, `tournamentPairingId?` | MS-Analytics (`game.events`), MS-Notifications (`notif.game.events`), MS-Tournament (`tournament.game.results.queue`, solo `game.finished`) |
| `game.invitation` | `gameId`, `playerId` (nullable si invitado sin cuenta), `inviterId`, `inviterName`, `gameUrl`, `email` | MS-Notifications (`notif.game.events`) |
| `game.rematch.created` | `gameId` (nueva sesión), jugadores y modalidad heredada | MS-Analytics / MS-Notifications (`game.*`) |
| `elo.updated` | `playerId`, `oldElo`, `newElo`, `delta`, `ratingType`, `gameId?` — se emite **uno por jugador** | MS-Users (`users.elo.queue`), MS-Notifications (`notif.game.events`) |

### Familia `rating.*` / `etl.*` — publica **MS-ETL** (Python)

| Evento | Payload (campos) | Consumidores |
|---|---|---|
| `rating.updated` | `playerId`/identificador externo, fuente (FIDE/AJEFECH/Lichess/Chess.com), rating | MS-Users (`users.rating.queue`), MS-Analytics (`etl.events`) |
| `sync.completed` | resumen del batch de sincronización | MS-Analytics / MS-Notifications (`etl.events`) |

---

## Eventos planificados — Release R1 ("Reloj y pagos")

> **Aún no implementados.** Se listan aquí como contrato acordado en Fase 0 antes de codificar.

### EP-P1 · Invitación con modalidad (formaliza el flujo actual)

Hoy existe `game.invitation` (push simple) pero **no** una máquina de estados con TTL.
R1 introduce el ciclo de vida completo:

| Evento | Publicador | Payload propuesto | Consumidores |
|---|---|---|---|
| `invite.created` | MS-Game | `inviteId`, `sessionId`, `fromPlayerId`, `toPlayerId`, `timeControlInitialMs`, `timeControlIncrementMs`, `color`, `expiresAt`, `ttlSeconds` | MS-Notifications (push in-app), bff-player |
| `invite.accepted` | MS-Game | `inviteId`, `sessionId`, `acceptedAt` | MS-Notifications |
| `invite.declined` | MS-Game | `inviteId`, `reason?` | MS-Notifications |
| `invite.expired` | MS-Game | `inviteId`, `sessionId`, `expiredAt` | MS-Notifications, bff-player (limpieza de estado) |

Bindings a definir: extender `notif.game.events` con `invite.*` **o** crear cola dedicada `notif.invite.queue` (decidir según volumen; recomendado dedicado por su TTL/limpieza).

### EP-O5 · Validación de pago de inscripción

| Evento | Publicador | Payload propuesto | Consumidores |
|---|---|---|---|
| `enrollment.payment.submitted` | MS-Tournament | `tournamentId`, `registrationId`, `playerId`, `documentKey` (Supabase Storage, privado), `amount`, `submittedAt` | MS-Notifications (avisa al organizador) |
| `enrollment.payment.reviewed` | MS-Tournament | `tournamentId`, `registrationId`, `playerId`, `decision` (`APPROVED`/`REJECTED`), `reason?`, `reviewedBy`, `reviewedAt` | MS-Notifications (mail al jugador), MS-Analytics (conciliación) |

Nuevo estado de inscripción: `PENDIENTE_VALIDACION` → `CONFIRMADA` / `RECHAZADA`.
Seguridad (O5-05): `documentKey` nunca es URL pública; se resuelve a URL firmada con expiración solo para organizador del torneo + dueño.

---

## Eventos planificados — Releases posteriores (referencia)

| Evento | Release | Épica |
|---|---|---|
| `player.provisional.created`, `player.invited`, `player.claimed` | R2 | EP-O1 (pre-registro y fusión provisorio→cuenta) |
| `challenge.created`, `challenge.progressed`, `achievement.unlocked` | R3 | EP-O4 (desafíos y logros) |

> `player.claimed` es el evento delicado: dispara la re-vinculación de historial/ratings/
> inscripciones en MS-Tournament y MS-Analytics. Debe ser transaccional en MS-Users y
> los consumidores idempotentes (ver riesgo O1-03 en el backlog).
