# Plan de demo ChessQuery

Guion ordenado y lista de refinamiento previo. Pensado para una demo de
~12 minutos con dos máquinas (o dos navegadores) visibles en pantalla.

## Pre-flight (5 min antes de empezar)

- [ ] `supabase status` muestra todo running. Si no: `supabase start`.
- [ ] `cd infrastructure && make up` y todos los containers `healthy`.
- [ ] `curl http://localhost:8080/actuator/health` → 200.
- [ ] Abrir Supabase Studio en otra pestaña (`http://127.0.0.1:54323`) — la vamos a usar dos veces.
- [ ] Resetear datos demo: `make reset && make up` si se quiere arrancar limpio (cuidado: borra todos los registros). Alternativa más suave: borrar a mano los usuarios de prueba previos en Studio → Authentication → Users.
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
   - Indicador 🟢 al lado del nombre del rival — *"presencia en tiempo real con Supabase Realtime"*.
   - Latencia entre máquinas: <300 ms.
7. **Máquina A** rinde (botón "Rendirse" → confirmar).
8. **Mostrar persistencia**:
   - Studio → Database → tabla `game` → última fila tiene `result=0-1`, `pgn_url=storage://...`.
   - Studio → Storage → `chessquery-pgn` → archivo `games/2026/05/{id}.pgn` descargable.
   - En la app: link "Partida guardada como #N" → abrir y mostrar PGN renderizado.

**Cierre del flujo 1**: *"Auth de Supabase, partida validada server-side jugada por jugada, persistencia del PGN en storage. Todo el ciclo de una partida queda atómicamente registrado"*.

---

## Flujo 2 — Organizador, torneo, inscripción, pairings

**Duración**: ~5 min. **Mensaje**: "el rol ORGANIZER tiene un panel separado y el ciclo torneo→inscripción→ronda funciona end-to-end".

> **REQUIERE FIX P0 ANTES DE LA DEMO**: hoy `POST /api/organizer/tournaments` falla con `400 INVALID_PARAMETER` porque el gateway propaga la UUID de Supabase en `X-User-Id` en vez del `player.id` numérico que ms-tournament espera. Sin este fix, el flujo se cae al crear el torneo.

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

### P0 — bloqueantes

- [ ] **Fix X-User-Id en gateway**: resolver supabase_user_id → player.id en `SupabaseJwtAuthFilter` y propagar el id numérico. Sin esto el flujo 2 NO corre.
- [ ] **Reset script para datos demo**: un `make demo-reset` que borre los users de prueba dejando los seed chilenos. Hoy `make reset` borra todo y hay que esperar el re-seed.
- [ ] **Probar flujos 1 y 2 de punta a punta una vez en serio**, cronometrando, en un setup limpio. Detectar fricciones de UX antes de que las descubra el evaluador.

### P1 — fuerte mejora visual / narrativa

- [ ] Reloj funcional (`clock_white_ms` / `clock_black_ms` ya existen en backend, falta tick + decremento).
- [ ] Sonido check ya está; agregar sonido de mate (reusar `notify` está OK pero un mate específico mejora).
- [ ] Persistir `lichessUsername` y `club` del Register (hoy se capturan en el form pero no se mandan al endpoint update Player).
- [ ] Pulido visual de la card "Invitar por email" — confirmar que el feedback ✓ Enviado dura suficiente y el error de email inválido es legible.

### P2 — quality-of-life si sobra tiempo

- [ ] Pre-moves (`chessground premovable.enabled=true`).
- [ ] Under-promotion UI (hoy fuerza dama).
- [ ] Oferta/aceptación de tablas.
- [ ] SSO entre chess-portal y organizer-panel (hoy hay que loguearse en cada una). Para la demo se puede tapar con dos pestañas privadas distintas y un email distinto por rol.

### P3 — post-demo

- [ ] Healthchecks de los servicios Java en docker-compose.
- [ ] `make build` o `make up --build` en el Makefile (hoy `make up` levanta imágenes stale si cambió el código).
- [ ] Limpiar `.env.example` (S3_*, MINIO_*, AUTH_DB_URL — todos servicios removidos).
- [ ] Borrar `infrastructure/scripts/init-minio.sh` (no se ejecuta).
- [ ] `spring.flyway.validate-on-migrate=false` en ms-game perfil dev.
- [ ] Reactivar MS-Notifications con UI inbox.

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
| Supabase Studio no carga | Es secundario; los datos se pueden mostrar via `psql` directo a `localhost:5433` (user_db) o vía las APIs REST. |
| Crash total y ya pasaron 3 min | Saltar al punto narrativo: enseñar el repo en GitHub, los commits recientes, la arquitectura del CONTEXT.md. La demo en vivo no es lo único que se evalúa. |
