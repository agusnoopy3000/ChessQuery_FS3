# Plan para mañana — refinamiento ChessQuery

Estado al cerrar hoy (2026-05-07):
- 53 commits en `feat/ajefech-integration`, todo verificado en local.
- Live game end-to-end OK (registro Supabase → partida → PGN limpio importable a lichess).
- 5 features de UX nuevos (modal fin partida, indicador turno, apertura inline, mobile responsive, revancha).
- Email magic link customizado con URL visible.
- PR a main creado.

---

## Bloque A — Bloqueantes (resolver antes de cualquier otra cosa) ⚠️

### A1. Fix `X-User-Id` en API Gateway
[api-gateway/.../SupabaseJwtAuthFilter.java:221](../api-gateway/src/main/java/cl/chessquery/gateway/filter/SupabaseJwtAuthFilter.java#L221) propaga la UUID de Supabase como `X-User-Id`. MS-Tournament lo bindea a `Long` (player.id) y rompe con `400 INVALID_PARAMETER`. Bloquea el flujo completo de torneos.

**Fix**:
1. Inyectar `WebClient` apuntando a MS-Users en el filtro.
2. Tras validar el JWT, llamar a `GET /users/by-supabase-id/{uuid}`.
3. Cachear la resolución en memoria (Caffeine, TTL 5 min, max 10k entries).
4. Propagar el `id` numérico en `X-User-Id`.
5. Si MS-Users responde 404 (player no existe aún por race con webhook), reintentar 1× después de 500ms; si sigue 404, devolver 503 al cliente con código `USER_NOT_RESOLVED`.

**Test**: registrar usuario ORGANIZER → crear torneo → debería llegar 201 (hoy llega 400).

**Esfuerzo**: 2 h.

### A2. Healthchecks de servicios Java en docker-compose
Hoy `docker compose ps` muestra "Up X minutes" sin discriminar healthy/unhealthy. Cuando un container está zombie (responde TCP pero el endpoint cuelga), no nos enteramos. Pasó hoy con MS-Storage tomando 14s en responder y haciendo restart de ms-game a mitad de un test.

**Fix**: agregar a cada servicio Java (gateway, ms-users, ms-tournament, ms-game, ms-analytics, ms-notifications):
```yaml
healthcheck:
  test: ["CMD", "wget", "-qO-", "http://localhost:${SERVICE_PORT}/actuator/health"]
  interval: 15s
  timeout: 3s
  retries: 5
  start_period: 90s
```

**Esfuerzo**: 30 min.

### A3. LAN IP en magic link redirect
GoTrue rechaza `http://192.168.1.186:5173/play/N` aunque esté en `additional_redirect_urls`. Solo funciona con `127.0.0.1` y `localhost`. Bloquea el flujo magic-link → mobile.

**Fix candidato 1**: probar con hostname en vez de IP (e.g. `chessquery.local` apuntando vía /etc/hosts a la LAN IP) — a veces GoTrue valida más permisivo con hostnames.

**Fix candidato 2**: revisar si `GOTRUE_SITE_URL` también necesita la LAN IP además de allow-list.

**Fix candidato 3**: investigar si es un bug conocido de GoTrue con IPs literales y wildcards — buscar issues en supabase/auth GitHub.

**Workaround para demo**: hacer la demo del magic link desde desktop (donde funciona perfecto). Mobile solo para mostrar el responsive del tablero.

**Esfuerzo**: 1-2 h investigando, posiblemente sin resolución limpia.

---

## Bloque B — Refinamientos UX/UI alto impacto

### B1. Material capturado y balance
Tira arriba/abajo del tablero con las piezas que cada lado capturó (♟♟♞ vs ♙♗) + delta numérico (`+3`). Se deriva del FEN, no requiere backend.

**Esfuerzo**: 30 min.

### B2. Reloj funcional
Los campos `clock_white_ms` / `clock_black_ms` ya existen en backend pero no decrementan. Implementar:
- Tick local cada 100ms del reloj activo (turno actual).
- Cada move incluye el clock al servidor.
- Frontend muestra `mm:ss` o `m:ss.d` (último decimal cuando <10s).
- Si un reloj llega a 0 → server detecta time-out y finaliza partida con resultado al rival.

**Esfuerzo**: 2 h.

### B3. Persistir `lichessUsername` y `club` en Register
Los campos se piden en el form de registro pero no se mandan al endpoint update Player. Conectar el flow.

**Esfuerzo**: 30 min.

### B4. Header con ELO y país junto al nombre
Hoy al lado del nombre solo está `⚪`/`⚫`. Lichess muestra: nombre · ELO · 🇨🇱. Datos ya están en `Player`. Le da look profesional.

**Esfuerzo**: 25 min.

### B5. Toast/snackbar de eventos Realtime
"Rival se conectó", "Rival se desconectó hace 30s", "Primera jugada del rival". Los sonidos cumplen pero un toast visual ayuda con audio off (el caso de demo en aula).

**Esfuerzo**: 30 min con `sonner` o componente propio.

---

## Bloque C — Refinamientos mid-impact

### C1. Pre-moves (`chessground premovable.enabled=true`)
Permite encolar la próxima jugada antes de que el rival mueva. Estándar en lichess. ~20 min.

### C2. Under-promotion picker
Hoy fuerza dama. Modal pequeño que pregunta Q/R/B/N cuando promueve. ~45 min.

### C3. Oferta/aceptación de tablas
Botón "Ofrecer tablas" → broadcast → rival acepta/rechaza → modal de fin. ~1 h backend + UI.

### C4. Navegación por la historia (← →)
Volver atrás en las jugadas para revisar (board read-only en posiciones intermedias). ~1 h.

---

## Bloque D — Higiene técnica

### D1. `make build` o `make up --build` en Makefile
Hoy `make up` levanta imágenes stale si cambió el código. Los devs olvidan rebuild manual constantemente. ~10 min.

### D2. Limpiar `.env.example`
Aún tiene `S3_*`, `MINIO_*`, `AUTH_DB_URL` (servicios removidos). ~10 min.

### D3. Borrar `infrastructure/scripts/init-minio.sh`
No se ejecuta. ~1 min.

### D4. `spring.flyway.validate-on-migrate=false` en ms-game (perfil dev)
Un cambio a un V file ya aplicado deja el container en restart-loop por checksum mismatch. Pasó 2 veces. ~5 min.

### D5. Tests del live game
No hay tests para `LiveGameService`. Mínimo: `LiveGameServiceTest` que cubra:
- Crear → join → 5 moves → resign → finishSession con PGN bien formado.
- Move ilegal → 400.
- Move fuera de turno → 403.
- Order de jugadas en PGN (regression test del bug de hoy).

**Esfuerzo**: 1.5 h.

---

## Bloque E — Pre-demo

### E1. Reset de datos para demo limpia
Un `make demo-reset` que:
- Borre todos los users de Supabase Auth excepto seed admins (si los hay).
- Borre todas las sesiones live + games + torneos demo.
- Mantenga los 10 jugadores chilenos seed (FECHAJ).

**Esfuerzo**: 1 h.

### E2. Dry-run completo de la demo
Cronometrado, en setup limpio, dos pestañas. Detectar fricciones de UX antes que el evaluador. Documentar qué falló y agregar al [PLAN_DEMO.md](./PLAN_DEMO.md) → sección "Plan B".

**Esfuerzo**: 30 min ejecución + 30 min ajustes.

### E3. Verificar que los flujos sugeridos del PLAN_DEMO funcionan
Hoy verificamos solo el flujo 1 (live game). Probar:
- Flujo 2: torneo (requiere A1 fix).
- Flujo 3: standings + ELO recalculado tras game.finished.
- Flujo 4: búsqueda fuzzy con pg_trgm.
- Flujo 5: circuit breaker MS-Users → MS-Game.

**Esfuerzo**: 1 h.

---

## Orden sugerido para mañana

| Hora aprox | Tarea | Bloque |
|---|---|---|
| 09:00 - 11:00 | A1 (X-User-Id fix) | A |
| 11:00 - 11:30 | A2 (healthchecks) | A |
| 11:30 - 12:30 | E3 (verificar flujos torneo + standings) | E |
| 12:30 - 13:00 | almuerzo | |
| 13:00 - 13:30 | B1 (material capturado) | B |
| 13:30 - 14:00 | B4 (ELO + país junto al nombre) | B |
| 14:00 - 16:00 | B2 (reloj funcional) | B |
| 16:00 - 17:00 | D5 (tests live game) | D |
| 17:00 - 17:30 | D1-D4 (higiene técnica rápida) | D |
| 17:30 - 18:30 | E2 (dry-run demo + ajustes) | E |

Total: ~9 h efectivas. Recortable: B2 (reloj) y D5 (tests) si aprietan los tiempos — son los más caros.

A3 (LAN IP) y B5/C1-C4 quedan para post-demo.

---

## Lo que NO hacer mañana

- Migrar a Supabase Cloud / AWS (ya está documentado en [PLAN_MIGRACION_CLOUD.md](./PLAN_MIGRACION_CLOUD.md), pero es trabajo de varios días).
- Setup de CI/CD (GitHub Actions) — post-demo.
- MS-Analytics / MS-Notifications / MS-ETL — quedan como pendientes documentados.
- Refactor mayor — si algo no está roto, no tocarlo a 24h del entregable.
