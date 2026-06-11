# Revisión de código — Fases 0–2 (línea base + transversal + servicios core)

> **Fecha:** 2026-06-10 · **Rama:** `worktree-review+auditoria-codigo` (aislada de `main`)
> **Alcance:** Fase 0 (línea base de tests/cobertura), Fase 1 (revisión transversal de
> arquitectura) y Fase 2 (ms-users, ms-game, ms-tournament). Pensado como insumo para un
> refactor posterior — **no se cambió código de producción** en esta revisión.
> Complementa (no duplica) `SECURITY_AUDIT_REPORT.md` y `ROADMAP_V1.md`.

---

## Fase 0 — Línea base verificada

Toda la suite Java se corrió **dentro de Docker** (`maven:3.9-eclipse-temurin-17`), lo que
de paso **valida el "Paso 0" de T1 del roadmap**: la suite completa corre en contenedor sin
Docker daemon ni Supabase (H2 en memoria), tal como promete `TESTING.md`.

| Módulo | Tests | Resultado | Cobertura instrucciones | Cobertura branches |
|---|---|---|---|---|
| api-gateway | 52 | ✅ | **97.5 %** | 77.5 % |
| ms-users | 112 | ✅ | **78.1 %** ⚠️ | 58.6 % |
| ms-game | 91 | ✅ | **93.1 %** | 75.6 % |
| ms-tournament | 102 | ✅ | **86.3 %** | 67.2 % |
| ms-notifications | 66 | ✅ | **96.4 %** | 72.4 % |
| ms-analytics | 14 | ✅ | **76.0 %** ⚠️ | 60.7 % |
| bff-player (jest) | 47 | ✅ | 95.4 % stmts | 71.6 % |
| bff-organizer (jest) | 28 | ✅ | 92.2 % stmts | 83.6 % |
| bff-admin | **0** | ❌ config rota | — | — |
| frontend chess-portal (vitest) | suite pasa | ✅ | 74.3 % stmts* | 73.8 % |
| ms-etl (pytest) | 11 definidos | no ejecutados | — | — |

\* La cobertura del frontend solo mide los archivos alcanzados por los specs existentes
(6 páginas del portal); `organizer-panel` no tiene tests.

**Lecturas clave para T1 del roadmap:**

1. **El 80 % en el core está mucho más cerca de lo que el roadmap asume** (estimaba 6–8
   días-dev). Por instrucciones, solo falta subir **ms-users (78.1 %)**; gateway, game y
   tournament ya superan el umbral. Si el gate se define **por branches**, sí falta trabajo
   en todos (58–77 %). Decisión a tomar en equipo: gate por instrucciones (alcanzable ya)
   o por branches (más exigente).
2. **bff-admin tiene 0 tests y la config de Jest está rota**: `package.json` no declara
   `ts-jest` ni `jest` en devDependencies (`npx jest` falla con "Module ts-jest was not
   found"). Arreglo barato y necesario antes de ponerle gate.
3. **ms-etl** define 11 tests (parser AJEFECH, circuit breaker, Lichess real) que no se
   ejecutaron en esta línea base; uno pega a la API real de Lichess (debería marcarse como
   integración/skippeable para CI). Entra con T3.

---

## Fase 1 — Hallazgos transversales

### F1-1 · Triplicación de código común en los BFFs (deuda, prioridad alta para refactor)

- `common/auth.guard.ts` y `common/health.controller.ts` son **copias byte a byte idénticas**
  en los 3 BFFs.
- `common/http.service.ts` son **3 variantes ya divergentes**:
  - **Timeout HTTP:** 15 000 ms en bff-player vs **5 000 ms** en organizer/admin. No hay
    razón documentada para la diferencia.
  - bff-player **no tiene `delete()`**; organizer/admin sí.
  - El comentario que explica la lista de errores transitorios reintentables solo existe
    en bff-player.

**Recomendación:** extraer un paquete compartido (`@chessquery/bff-common` en un workspace
npm, igual que ya se hace en `frontend/packages/ui-lib`) o, como mínimo, re-sincronizar las
3 copias y documentar el timeout elegido. Cada fix futuro en uno de estos archivos hoy hay
que repetirlo 3 veces (y ya se olvidó 2 veces).

### F1-2 · Restos de la ruta ms-etl vieja (código muerto / funcionalidad rota en AWS)

- `bff-organizer/src/common/http.service.ts` declara `msEtl` y `MS_ETL_URL` pero **ningún
  código del organizer lo usa** → config muerta, eliminar (mismo cleanup que ya se hizo en
  bff-player con `ae73be9`).
- `bff-admin/src/admin/admin.service.ts` **sí usa ms-etl activamente** (`/etl/status`,
  `/etl/logs`, `/etl/sync/{source}`): esas secciones del panel admin **fallan hoy en AWS**
  porque ms-etl no está desplegado. Documentar como limitación conocida hasta T3, y
  verificar que el panel degrade con gracia (el `settle()` de `getOverview` sí tolera el
  fallo; los endpoints directos devuelven error).

### F1-3 · Contrato de errores: consistente, pero cuadruplicado

Los 4 servicios Java core tienen `ApiException` + `GlobalExceptionHandler` propios con el
mismo formato — bien por consistencia, pero son 4 copias que pueden divergir (igual que los
BFFs). **ms-notifications no tiene ninguno** (no expone API REST significativa, aceptable).
Para el refactor: evaluar un módulo común (`chessquery-commons`) para excepciones, formato
de error y DTOs compartidos. Trade-off: acopla los despliegues; decidir en equipo.

### F1-4 · Configuración: en buen estado

- Frontend: URLs cross-app y API vía `VITE_*` con fallback localhost — correcto (el fix del
  PR #20 quedó bien hecho; no se encontraron más URLs horneadas).
- Gateway: rate-limiting por IP en las 3 rutas BFF, pool con eviction, timeouts sanos.
- `application-aws.yml` limita actuator a `health,info` (en local expone `gateway`, ok).
- Los guards H-03 de Martin cubren JWT y webhook secret. **No cubren** `REDIS_PASSWORD` /
  `RABBITMQ_PASSWORD` (default `chessquery_dev` en los yml) — riesgo menor porque RabbitMQ/
  Redis solo escuchan dentro de la task, pero anotarlo para T2.

### F1-5 · Calidad del código del gateway: alta

`SupabaseJwtAuthFilter` (JWKS con refresh en background no bloqueante, fallback HMAC,
guard H-03) y `PlayerIdResolver` (cache Caffeine, retry solo en fallos transitorios,
auto-provisión desde claims) están bien diseñados y bien comentados. Dos notas menores:

- `isPublicPath` usa `startsWith`: `/actuatorX` o `/auth/loginX` también serían públicos.
  Cosmético hoy; usar match por segmento al refactorizar.
- La extracción del rol desde `user_metadata` es exactamente **H-02** (el usuario puede
  editar su propio `user_metadata` en Supabase) — ya está en el audit, solo recordar que
  el orden correcto post-fix es `app_metadata` primero.

---

## Fase 2 — Servicios core

### ms-users — bueno, con 3 puntos para el refactor

Lo positivo: `PlayerService.provisionBySupabaseId` maneja idempotencia, carreras
(`DataIntegrityViolationException` + re-lectura del ganador) y el poison-loop de
`lichess_username` correctamente y con comentarios que explican el porqué.

1. **Normalización de email inconsistente** (`PlayerService.java`):
   `getProfileByEmail()` busca con `email.trim().toLowerCase()`, pero `provision` y
   `syncFromAuth` **guardan el email tal cual llega** y `provision` busca match sin
   normalizar. Un email registrado con mayúsculas rompe el match por email (riesgo de
   perfil duplicado o invitación que no encuentra al jugador). Fix barato: normalizar a
   lowercase en un único punto al escribir y al buscar.
2. **`tryClaimFederated` muta la clave primaria** (`UPDATE player SET id = :newId`),
   apoyándose en FKs `ON UPDATE CASCADE`. Funciona, pero es frágil: cualquier FK futura
   sin cascade rompe el reclamo silenciosamente. Para el refactor: considerar una columna
   de mapeo (`claimed_from_id`) en vez de mutar la PK.
3. **Dos flujos de alta paralelos**: `syncFromAuth` (id = auth.userId, vía
   `bff-player/sync.controller`) y `provisionBySupabaseId` (UUID Supabase → id numérico).
   Mantener ambos duplica reglas de negocio (asignación de lichess, club, nombres default).
   Verificar si `/users/sync` sigue teniendo callers reales; si no, retirarlo.

Menor: `updateElo` hace read-modify-write sin lock (dos updates concurrentes del mismo tipo
de rating pueden pisarse). Riesgo bajo con el volumen actual.

### ms-tournament — el hueco grande es autorización (amplía H-04)

`TournamentController` revisado endpoint por endpoint:

| Endpoint | Chequeo actual | Problema |
|---|---|---|
| `DELETE /{id}` | rol + **propiedad** ✅ | el único completo |
| `PATCH /{id}/status` | solo rol | cualquier organizador opera torneos ajenos |
| `POST /{id}/rounds/{n}` | solo rol | ídem |
| `PATCH /registrations/{id}/approve·reject` | solo rol | ídem |
| `PATCH /pairings/{id}/result` | **ninguno** | cualquier usuario autenticado registra resultados de cualquier partida |
| `POST /{id}/registrations` | **ninguno** | el `playerId` viene del body: se puede inscribir a otro jugador |

Mientras H-02 siga abierto, el chequeo de rol es decorativo (el rol es falsificable). La
corrección es la que el propio audit propone para H-04: propagar `X-User-Id` y validar
propiedad en **todas** las acciones de escritura, no solo en delete. Esto cubre además el
caso explícito que Martin agregó a T4 ("un organizador no puede modificar torneos de otro")
— hoy ese caso de QA **falla por API directa**.

Dos notas de diseño:

- `generateRound` hace llamadas HTTP a ms-game **dentro de `@Transactional`**: si la
  transacción aborta después de crear las sesiones, quedan partidas huérfanas en ms-game,
  y los eventos de invitación se publican antes del commit. Tolerable en v1 (está
  parcialmente defendido: `LiveGameClient` devuelve null en fallo y la ronda sobrevive),
  pero para escalar conviene mover la creación de partidas a un paso post-commit
  (`TransactionalEventListener` o outbox).
- `deleteTournament` borra pairings/rounds con `findBy…` + `deleteAll` (N+1 queries).
  Irrelevante a esta escala; cambiar a `deleteByRoundId` derivado si se refactoriza.

### ms-game — el de mejor calidad de los tres

`LiveGameService`: lock pesimista por sesión para serializar jugadas, retry idempotente de
move y de finish, validación de legalidad con chesslib, broadcast del estado completo para
ahorrar round-trips. El BFF deriva `playerId` del usuario autenticado (header), no del body
— la identidad en jugadas es sólida a nivel BFF.

1. **Los relojes los reporta el cliente** (`clockWhiteMs/clockBlackMs` en `MoveRequest` y
   el endpoint `/timeout` lo invoca el cliente): un cliente modificado puede mentir su
   tiempo restante o no reclamar timeout. Para v1 (partidas amistosas/torneo presencial)
   es aceptable; **documentarlo como limitación conocida** y, post-v1, validar el reloj
   server-side contra `lastMoveAt`.
2. `invitePlayer` no es `@Transactional` pero publica eventos y consulta ms-users vía
   `RestTemplate` bloqueante con la URL armada a mano — funciona, pero es el único lugar
   de ms-game que no usa el patrón de cliente con timeout/retry. Unificar con un client
   como `UserEloClient` de ms-tournament.
3. El fix del ELO PLATFORM (`ccc24fa`) quedó consistente: `RegisterGameRequest` →
   `ratingType PLATFORM` separado del federativo.

---

## Resumen priorizado para el refactor

| # | Hallazgo | Tipo | Esfuerzo | ¿Cuándo? |
|---|---|---|---|---|
| 1 | Autorización por propiedad en TODAS las escrituras de ms-tournament (amplía H-04; `recordResult` y `joinTournament` sin chequeo alguno) | seguridad | ~1 día | con T2/T4 |
| 2 | bff-admin: arreglar config Jest + tests mínimos | calidad | ~½ día | con T1 |
| 3 | ms-users a 80 % (es el único core bajo el umbral) | calidad | ~½–1 día | con T1 |
| 4 | Normalización de emails en ms-users (un solo punto de verdad) | bug latente | ~2 h | con T1 |
| 5 | Unificar/re-sincronizar `http.service.ts` de los 3 BFFs (timeout 15 s vs 5 s) + borrar `msEtl` muerto del organizer | deuda | ~½ día | refactor |
| 6 | Decidir gate de cobertura: instrucciones (ya casi) vs branches (falta) | decisión | reunión | ya |
| 7 | Consolidar flujos de alta de ms-users (sync vs provision) | deuda | ~1 día | refactor |
| 8 | Relojes server-side en live game | limitación | post-v1 | backlog |
| 9 | Llamadas HTTP fuera de transacción en `generateRound` (outbox/post-commit) | deuda | post-v1 | backlog |
| 10 | Módulo común para ApiException/handlers (4 copias) y paquete BFF común | deuda | post-v1 | backlog |

**Conclusión general:** el código está en mejor estado del que el roadmap asume — suites
verdes en los 6 módulos Java + 2 BFFs (512 tests Java, 75 Node), cobertura core casi al
80 %, y los componentes críticos (gateway, live game) bien diseñados y comentados. La deuda
real está concentrada en (a) autorización por propiedad en ms-tournament, (b) duplicación
entre BFFs, y (c) los flujos dobles de alta en ms-users. Nada bloquea el alcance actual de
la demo; todo lo de arriba es para el refactor pos-entrega salvo los ítems 1–4, que
conviene colar dentro de T1/T2/T4 porque tocan lo mismo que esas tareas ya tocan.
