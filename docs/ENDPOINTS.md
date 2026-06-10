# ChessQuery — Referencia de endpoints (para el equipo)

> **Qué es este doc:** la referencia en español de TODAS las rutas de la plataforma:
> qué hace cada una, quién la llama y qué headers exige. Complementa a
> [`ENDPOINTS_QA.md`](./ENDPOINTS_QA.md) (que explica cómo *probarlas* con curl/token).
> Si agregás o cambiás un endpoint, actualizá la tabla correspondiente acá.

## Cómo leer una ruta (la cadena completa)

Toda llamada desde internet recorre: **frontend → API Gateway → BFF → microservicio**.

```
  navegador                gateway (:8080)           BFF (Nest)            microservicio (Spring/FastAPI)
  ────────────             ───────────────           ──────────            ──────────────────────────────
  /api/player/me/dashboard → valida JWT, resuelve    → /player/me/dashboard → GET ms-users /users/{id}/profile
                             X-User-Id y rutea         (arma la respuesta     GET ms-analytics /analytics/...
                             por prefijo               juntando varios MS)
```

- El **gateway** valida el JWT de Supabase y agrega los headers de identidad
  (`X-User-Id` = id numérico del Player, `X-Supabase-User-Id`, `X-User-Email`, `X-User-Role`).
- El **BFF** habla con los microservicios y agrega los headers que el MS exige
  (los MS confían en esos headers — por eso solo son alcanzables dentro de la red interna).
- Prefijos del gateway: `/api/player/**` → bff-player · `/api/organizer/**` → bff-organizer ·
  `/api/admin/**` → bff-admin (con `StripPrefix=1`: `/api/player/X` llega al BFF como `/player/X`).

**Públicas (sin JWT):** `/actuator/health/*` y `/webhooks/*` (el webhook valida su propio secreto).

---

## 1. Portal del jugador — `/api/player/**` (bff-player)

Requieren `Authorization: Bearer <JWT>`. El BFF toma la identidad de `X-User-Id` (nunca del body).

### Perfil y datos

| Ruta (tras el gateway) | Método | Qué hace | MS de fondo |
|---|---|---|---|
| `/player/me/dashboard` | GET | Dashboard del usuario logueado (perfil + stats + rating) | ms-users, ms-analytics |
| `/player/me/rating-chart` | GET | Serie histórica de rating para el gráfico | ms-users |
| `/player/:id/profile` | GET | Perfil público de cualquier jugador | ms-users |
| `/player/:id/lichess` | GET | Ratings oficiales de Lichess del jugador (sync directo) | ms-users → API Lichess |
| `/player/search?q=` | GET | Búsqueda difusa de jugadores por nombre | ms-users |
| `/player/rankings` | GET | Ranking de jugadores de la plataforma | ms-users |
| `/player/sync` | POST | Upsert del perfil tras registro (flujo legado, ver §5) | ms-users `/users/sync` |

### Partidas en vivo (tablero en tiempo real)

| Ruta | Método | Qué hace |
|---|---|---|
| `/player/play/live` | POST | Crea una partida en vivo (casual: queda WAITING; torneo: ACTIVE) |
| `/player/play/live/:id` | GET | Estado completo de la partida (FEN, relojes, jugadas) |
| `/player/play/live/:id/join` | POST | El rival se une (pasa a negras, arranca el reloj) |
| `/player/play/live/:id/move` | POST | Envía una jugada (UCI); el server valida legalidad y turno |
| `/player/play/live/:id/resign` | POST | Abandona la partida |
| `/player/play/live/:id/draw` | POST | Registra tablas acordadas (la negociación va por Realtime) |
| `/player/play/live/:id/timeout` | POST | Reclama victoria por tiempo |
| `/player/play/live/:id/rematch` | POST | Crea la revancha con colores invertidos |
| `/player/play/live/:id/invite` | POST | Invita por email (notificación in-app + correo si hay SMTP) |
| `/player/play/find-match` | POST | Emparejamiento casual automático |
| `/player/play/games` | POST | Registra una partida ya jugada (no en vivo) |

Todas pegan a **ms-game** (`/games/live/*`).

### Torneos (vista jugador)

| Ruta | Método | Qué hace |
|---|---|---|
| `/player/tournaments` | GET | Torneos visibles para inscribirse |
| `/player/tournaments/:id` | GET | Detalle de un torneo |
| `/player/tournaments/:id/standings` | GET | Tabla de posiciones |
| `/player/tournaments/:id/round/:n` | GET | Ronda con sus emparejamientos |
| `/player/tournaments/:id/my-registration` | GET | Mi estado de inscripción en ese torneo |
| `/player/tournaments/:id/register` | POST | **Inscribirse** al torneo (PENDING si requiere aprobación) |

Todas pegan a **ms-tournament** (`/tournaments/*`).

### Notificaciones (campana)

| Ruta | Método | Qué hace |
|---|---|---|
| `/player/notifications` | GET | Inbox de notificaciones in-app |
| `/player/notifications/unread-count` | GET | Contador para el badge de la campana |
| `/player/notifications/:id/read` | POST | Marca una como leída |
| `/player/notifications/read-all` | POST | Marca todas como leídas |

Pegan a **ms-notifications** (`/notifications/*`). El organizer tiene las mismas 4 bajo `/organizer/notifications/*`.

---

## 2. Panel del organizador — `/api/organizer/**` (bff-organizer)

Requieren JWT con rol ORGANIZER. El BFF agrega `X-User-Role: ORGANIZER` y `X-User-Id` hacia ms-tournament.

| Ruta | Método | Qué hace |
|---|---|---|
| `/organizer/tournaments` | GET | **Mis** torneos (el BFF filtra por organizerId) |
| `/organizer/tournaments` | POST | Crear torneo (queda en DRAFT) |
| `/organizer/tournaments/:id` | GET | Detalle del torneo |
| `/organizer/tournaments/:id/status` | PATCH | Transición de estado (DRAFT→OPEN→IN_PROGRESS→FINISHED) |
| `/organizer/tournaments/:id` | DELETE | Eliminar torneo (solo dueño; solo DRAFT/OPEN sin rondas) |
| `/organizer/tournaments/:id/join` | POST | **Inscribir a un jugador** al torneo (lo hace el organizador) |
| `/organizer/tournaments/:id/registrations` | GET | Inscripciones, enriquecidas con nombre/ELO |
| `/organizer/registrations/:rid/approve` | PATCH | Aprobar inscripción PENDING |
| `/organizer/registrations/:rid/reject` | PATCH | Rechazar inscripción PENDING |
| `/organizer/tournaments/:id/rounds/:n/generate` | POST | Generar la ronda *n* (empareja y crea partidas en vivo) |
| `/organizer/tournaments/:id/round/:n` | GET | Ver ronda enriquecida con nombres |
| `/organizer/pairings/:pid/result` | PATCH | Cargar resultado manual de una mesa |
| `/organizer/tournaments/:id/standings` | GET | Posiciones (Buchholz / Sonneborn-Berger) |

---

## 3. Panel admin — `/api/admin/**` (bff-admin)

| Ruta | Método | Qué hace |
|---|---|---|
| `/admin/dashboard` | GET | Vista global: usuarios, torneos activos, partidas, analytics, ETL (degrada con gracia si un MS está caído) |
| `/admin/users` / `/admin/users/search` | GET | Listado/búsqueda de usuarios |
| `/admin/etl/status` | GET | Estado del ETL ⚠️ |
| `/admin/etl/logs?limit=` | GET | Logs del ETL ⚠️ |
| `/admin/etl/sync/:source` | POST | Dispara sincronización de una fuente ⚠️ |

> ⚠️ Las rutas de ETL fallan en AWS hasta integrar ms-etl (T3 del roadmap).

---

## 4. Rutas internas (no pasan por los BFFs)

| Ruta | Quién la llama | Qué hace |
|---|---|---|
| `POST /webhooks/supabase/user-registered` (gateway) | Supabase (trigger pg_net) | Publica `user.registered` en RabbitMQ → ms-users crea el Player |
| `GET /users/by-supabase-id/{uuid}` (ms-users) | Gateway (`PlayerIdResolver`) | Resuelve UUID de Supabase → id numérico (cacheado 5 min) |
| `POST /users/provision` (ms-users) | Gateway | Auto-provisiona el Player si el webhook aún no llegó |
| `GET /users/by-email?email=` (ms-users) | ms-game (invitaciones) | Busca jugador por correo para la notificación in-app |
| `PUT /users/{id}/elo` (ms-users) | interno | Actualiza un rating y registra historial |
| `PATCH /tournaments/pairings/{id}/result` (ms-tournament) | consumer `game.finished` + bff-organizer | Asienta el resultado de la mesa |
| `GET /actuator/health/*` (todos) | ALB / Docker healthcheck | Liveness/readiness |

**Eventos RabbitMQ** (asíncronos, no HTTP): `user.registered`, `game.finished`, `game.invitation`,
`elo.updated`, `rating.updated`, `tournament.*`. Los consumers viven en `messaging/` de cada MS.

---

## 5. Convenciones y renombres propuestos (decisión de equipo, post-v1)

**Convención actual (mantener):** recursos REST en inglés y en plural (`/tournaments/{id}/registrations`),
acciones como sub-recurso (`/move`, `/approve`). Las descripciones para humanos van en español en el
Swagger (`@Operation`) y en este doc — **el nombre de la URL no se traduce**: renombrar rutas rompe
frontend, BFFs, gateway y la demo desplegada a la vez.

Inconsistencias detectadas en la auditoría (renombrar recién cuando se pueda tocar front+BFF juntos):

| Hoy | Propuesta | Por qué |
|---|---|---|
| bff-player `POST /tournaments/:id/register` vs bff-organizer `POST /tournaments/:id/join` | unificar en `/registrations` (POST) en ambos | Son la MISMA acción (crear inscripción) con dos nombres distintos; el MS ya la llama `/registrations` |
| `GET /tournaments/:id/round/:n` (singular) vs `POST .../rounds/:n/generate` (plural) | todo plural: `/rounds/:n` | El MS usa plural; el singular del BFF es un typo heredado |
| ms-users `POST /users/sync` y `POST /users/provision` | dejar solo `provision` | Dos flujos de alta paralelos con reglas casi iguales (ver informe de revisión §ms-users); consolidar y retirar `sync` |
| ms-users `GET /users/ranking` vs bff-player `GET /rankings` | unificar en `rankings` | Mismo recurso, singular/plural mezclado |
| bff-admin `GET /users` + `GET /users/search` | mantener, pero documentar la diferencia | `users` pagina, `search` es difuso; el nombre no lo dice |

> Regla práctica para nuevos endpoints: **¿otro miembro del equipo puede adivinar qué hace solo por
> la URL y el método?** Si no, o el nombre está mal o falta el `@Operation` en español.
