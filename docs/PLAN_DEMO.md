# Plan de demo ChessQuery

Guion ordenado y lista de refinamiento previo. Pensado para una demo de
~12 minutos con dos máquinas (o dos navegadores) visibles en pantalla.

## Pre-flight (5 min antes de empezar)

- [ ] `supabase status` muestra todo running. Si no: `supabase start`.
- [ ] `cd infrastructure && make up` y todos los containers `healthy` (R2 — healthchecks `/actuator/health` activos).
- [ ] Resetear datos: `make demo-reset` (R18 — preserva los 10 jugadores chilenos seed y 90 aperturas ECO; borra usuarios `*@demo.cl`, sesiones live, games, torneos DRAFT/OPEN). Más rápido y seguro que `make reset && make up`.
- [ ] Verificar healthchecks: `for p in 8080 8081 8082 8083 8084 8085; do curl -fs http://localhost:$p/actuator/health | grep -q '"UP"' && echo "$p OK" || echo "$p DOWN"; done`.
- [ ] Abrir Supabase Studio en otra pestaña (`http://127.0.0.1:54323`) — la vamos a usar dos veces.
- [ ] Sonido del laptop ON (sin mute) para que se escuchen los audios de jugada.
- [ ] Volumen de notificaciones del sistema OFF (para que no aparezca el mailpit popup en pantalla).
- [ ] Mailpit abierto en otra pestaña (`http://127.0.0.1:54324`) — vamos a mostrar el magic link en vivo.

---

## Flujo 1 — Registro + autenticación + partida en vivo + persistencia PGN

**Duración**: ~4 min. **Mensaje**: "todo el ciclo de una partida casual queda guardado correctamente".

### Pasos

1. **Máquina A** abre `http://localhost:5173` → Register.
   - email: `ana@demo.cl`, password, firstName `Ana`, lastName `García`, role `PLAYER`.
   - Mostrar la transición: registrarse autologuea y cae en el portal.
2. **Mostrar en Supabase Studio** (pestaña ya abierta) → Authentication → Users → aparece `ana@demo.cl`. Mensaje: *"el JWT lo emite Supabase, nuestros microservicios lo validan vía JWKS"*.
3. **Máquina B** repite con `bruno@demo.cl`, `Bruno Pérez`.
4. **Mostrar en Studio** → Database → Table editor → `user_profiles` o vía MS-Users `/users` → ambos players ya tienen registro. Explicar: *"un webhook de Supabase dispara la creación del Player en MS-Users vía RabbitMQ"*.
5. **Máquina A** → "Empezar partida en vivo" → comparte el link.
   - Opción X — clipboard tradicional: copiar URL → pegar en máquina B.
   - **Opción Y (preferida) — Magic Link**: ingresar `bruno@demo.cl` en la nueva caja "Invitar por email" → click. Mostrar Mailpit recibiendo el correo en tiempo real → click en el link → Bruno entra directo a la partida.
6. **Jugar 4–6 jugadas**. Resaltar:
   - Sonidos de jugada (move/capture/check) — *"feedback sensorial estándar de plataformas como lichess"*.
   - Header de jugador estilo lichess: nombre · ELO · 🇨🇱 (R7). Si no hay ELO: "Sin rating".
   - Material capturado y delta debajo de cada jugador (R4) — derivado del FEN client-side.
   - Indicador 🟢 al lado del nombre del rival + toasts "Rival conectado/desconectado" (R8).
   - Indicador pulsante "Es tu turno" arriba del board.
   - Apertura detectada inline (📖 ECO + nombre).
   - Pre-moves habilitados (R9): mientras esperás al rival, podés encolar tu próxima jugada.
   - Promoción con picker Q/R/B/N (R10) si llega un peón a la última fila.
   - Botón ← → para revisar la historia de jugadas (R12) — board read-only en posiciones intermedias.
   - Botón "🤝 Ofrecer tablas" (R11) — broadcast Realtime, modal en el rival, cierre `1/2-1/2` por acuerdo.
7. **Máquina A** rinde (botón "Rendirse" → confirmar) — modal de fin con resultado grande y CTA "Revancha".
8. **Mostrar persistencia**:
   - Studio → Database → tabla `game` → última fila tiene `result=0-1`, `pgn_url=storage://...`.
   - Studio → Storage → `chessquery-pgn` → archivo `games/2026/05/{id}.pgn` descargable.
   - En la app: link "Partida guardada como #N" → abrir y mostrar PGN renderizado.

**Cierre del flujo 1**: *"Auth de Supabase, partida validada server-side jugada por jugada, persistencia del PGN en storage. Todo el ciclo de una partida queda atómicamente registrado"*.

---

## Flujo 2 — Organizador, torneo, inscripción, pairings

**Duración**: ~5 min. **Mensaje**: "el rol ORGANIZER tiene un panel separado y el ciclo torneo→inscripción→ronda funciona end-to-end".

> **R1 resuelto**: el `SupabaseJwtAuthFilter` ya resuelve UUID Supabase → `player.id` numérico vía `PlayerIdResolver` con caché Caffeine (TTL 5 min, max 10k entradas). Si MS-Users no responde tras 1 retry, devuelve `503 USER_NOT_RESOLVED`.

### Pasos

1. **Máquina C** (pestaña distinta) abre `http://localhost:5174` (organizer-panel) → Register.
   - email: `carla@demo.cl`, role `ORGANIZER`. Explicar: *"organizer-panel y chess-portal son SPAs separadas, cada una valida el rol del JWT"*.
   - Demostrar role guard: si Carla intenta entrar a `localhost:5173` con su cuenta de organizadora, ve "Acceso restringido" — el panel del jugador no la deja entrar (y viceversa).
2. **Carla crea un torneo**:
   - Nombre: "Demo Cup 2026".
   - Formato: SWISS.
   - Maxplayers: 8, rounds: 3.
   - Start date: hoy + 1 día.
3. **Mostrar en Studio** → tabla `tournament` en `tournament_db` → fila nueva, status `OPEN`.
4. **Máquina A (Ana, player)** → tab "Torneos" → "Demo Cup 2026" aparece listado → click → "Inscribirme".
5. **Máquina B (Bruno)** → mismo flujo → inscripción.
6. **Carla (organizer)** → torneo → tab "Inscripciones" → ve a Ana y Bruno con status `PENDING`.
7. **Carla valida ambas inscripciones** → status pasa a `CONFIRMED`. *"Aquí el organizador filtra ELO mínimo, cupos, etc — la lógica de validación está en MS-Tournament"*.
8. **Carla genera la ronda 1** → "Generar emparejamientos".
9. **Mostrar el pairing**: Ana vs Bruno (con colores asignados por el algoritmo Swiss). Studio → tabla `tournament_pairing` → fila nueva con `white_player_id` y `black_player_id`.
10. **Cierre del flujo**: *"Detrás del Factory Method de pairing strategies tenemos Swiss, Round Robin y Knockout — el organizador elige formato y el motor genera la ronda con los desempates correctos: Buchholz, Sonneborn-Berger"*.

---

## Flujo 3 (sugerido, +2 min) — Reportar resultado y standings

**Mensaje**: "el ciclo torneo se cierra: jugar la partida pareada, ingresar el resultado, ver standings actualizados, ELO recalculado".

### Pasos

1. Ana y Bruno juegan la partida emparejada en el flujo 2 (aprovechan que ya hay sesión live abierta del flujo 1, o crean una nueva — la partida del torneo se "ata" al pairing).
2. Carla → tab "Standings" del torneo → ve a Ana 1.0 / Bruno 0.0 (o 0.5/0.5 si tablas).
3. **Mostrar en Studio**:
   - tabla `game` (game_db) → la partida del torneo tiene `tournament_id` distinto de null.
   - Ratings actualizados en `player` (user_db) — `elo_national` cambió.
4. Mensaje: *"el evento `game.finished` viaja por RabbitMQ a MS-Users, que recalcula ELO con K=32 y publica `elo.updated`"*.

> **NICE-TO-HAVE**: hoy el formulario para reportar resultado del torneo está pendiente del lado organizer. Si no llega para la demo, este flujo se reduce a "mostrar que `tournament_pairing` tiene el resultado y los standings ya muestran 1.0/0.0".

---

## Flujo 4 (sugerido, +1 min) — Búsqueda de jugadores y perfil público

**Mensaje**: "ChessQuery indexa jugadores chilenos reales, no solo los de prueba".

### Pasos

1. En Studio mostrar que la tabla `player` tiene **10 jugadores chilenos seed** (Pablo Salinas, Cristóbal Henríquez, etc.) cargados desde `V6__seed_chilean_players.sql`.
2. En el chess-portal → buscador de jugadores → tipear "Salin" → aparece el match con búsqueda fuzzy (`pg_trgm`).
3. Click → perfil público con ELO nacional, club, ELO FIDE.
4. Mensaje: *"trabajamos con un dataset semilla de FECHAJ; el MS-ETL en backlog sincronizará automáticamente con FIDE y Lichess"*.

---

## Flujo 5 (sugerido, +1 min) — Resiliencia / circuit breakers

**Mensaje**: "el sistema tolera caídas parciales — no es un monolito disfrazado".

### Pasos

1. Mientras hay un torneo en marcha, parar MS-Users: `docker stop chessquery_ms_users`.
2. Crear una nueva partida live (el ELO de seed cae en fallback K=32).
3. Mensaje: *"Resilience4j con Circuit Breaker: cuando MS-Users no responde, MS-Game usa ELO 1500 default y el flujo no se rompe"*.
4. `docker start chessquery_ms_users` → todo vuelve a la normalidad sin restart de los demás servicios.

> **CUIDADO**: este flujo es impactante pero frágil — hace falta probarlo varias veces antes para que no quede colgado el container de Game esperando timeout. Si no lo ensayan, déjenlo afuera.

---

## Lista de refinamiento (orden de prioridad para la demo)

### Estado al 2026-05-08 (después de la rama `feat/pre-demo-refinement`)

**Hecho ✅**

- [x] R1 — `X-User-Id` resolución UUID Supabase → `player.id` en gateway con caché Caffeine.
- [x] R2 — healthchecks `/actuator/health` en api-gateway + 5 microservicios Java.
- [x] R4 — material capturado + delta visual debajo de cada jugador.
- [x] R6 — `lichessUsername` y `clubName` persisten desde el Register vía webhook → MS-Users.
- [x] R7 — header estilo lichess: nombre · ELO · 🇨🇱.
- [x] R8 — toast queue para eventos Realtime.
- [x] R9 — pre-moves habilitados.
- [x] R10 — picker Q/R/B/N para promoción.
- [x] R11 — oferta/aceptación de tablas (broadcast Realtime + endpoint `/games/live/{id}/draw`).
- [x] R12 — navegación ← → por historial.
- [x] R13 — `make build` (--no-cache) + `make up` con build incremental.
- [x] R14 — `.env.example` limpio.
- [x] R15 — `init-minio.sh` borrado.
- [x] R16 — Flyway `validate-on-migrate=false` en ms-game perfil dev.
- [x] R17 — `LiveGameServiceTest` (3 casos: full flow + PGN bien formado, move ilegal → 400, fuera de turno → 403).
- [x] R18 — `make demo-reset` + `scripts/demo-reset.sh`.

**Pendiente — P1**

- [ ] R5 — reloj funcional (tick 100ms, sync server, time-out → resultado al rival). ~3h.

**Pendiente — investigación / verificación**

- [ ] R3 — magic link LAN IP (workaround: demo desde desktop).
- [ ] R19 — dry-run cronometrado, dos pestañas, setup limpio.
- [ ] R20 — verificar flujos sugeridos (torneo end-to-end, standings + ELO, búsqueda fuzzy, circuit breaker MS-Users).

**Post-demo / opcional**

- [ ] N1 — inbox de notificaciones (campana 🔔 con MS-Notifications real).
- [ ] N2 — email transaccional al cierre de partida (Mailpit + plantilla).
- [ ] N3 — presencia de espectadores ("👁 N viendo").
- [ ] SSO entre chess-portal y organizer-panel.

---

## Cosas que NO hay que demostrar

- **Cloud**. Hoy todo corre local; la migración a Supabase Cloud / AWS está documentada en [PLAN_MIGRACION_CLOUD.md](./PLAN_MIGRACION_CLOUD.md) pero es trabajo posterior a la demo.
- **MS-Analytics, MS-Notifications, MS-ETL**. Marcados como pendientes; mencionar al pasar pero no abrir.
- **CI/CD**. No existe `.github/workflows/`. Si preguntan, decir que está planificado para después de la merge a main.

---

## Plan B — qué hacer si algo se rompe en vivo

| Síntoma | Plan B |
|---|---|
| Magic link no llega | Volver a "copiar URL y pegar". Mostrar Mailpit como evidencia técnica de que el envío salió pero al evaluador le da igual. |
| Tablero se queda pegado | Refrescar la pestaña. La rehidratación al `visibilitychange` lo trae de vuelta sin perder estado. |
| Container crashea | `docker compose restart <servicio>` — los datos están en volumes, no se pierde nada. Tener este comando en el portapapeles. |
| Healthcheck dice unhealthy en algún ms | `make ps` para ver cuál; `docker logs chessquery_<svc>` para diagnosticar; `docker compose restart <svc>` resuelve la mayoría de los casos. |
| Gateway responde 503 USER_NOT_RESOLVED | Race entre el JWT de Supabase y el consumer del webhook user.registered. Esperar 1s y retry; si persiste, recrear el usuario en Studio. |
| Modal de promoción no aparece | Verificar versión publicada del frontend (R10 pide modal Q/R/B/N). Plan B: avisar y promocionar a dama "como en la práctica". |
| Supabase Studio no carga | Es secundario; los datos se pueden mostrar via `psql` directo a `localhost:5433` (user_db) o vía las APIs REST. |
| Crash total y ya pasaron 3 min | Saltar al punto narrativo: enseñar el repo en GitHub, los commits recientes, la arquitectura del CONTEXT.md. La demo en vivo no es lo único que se evalúa. |
