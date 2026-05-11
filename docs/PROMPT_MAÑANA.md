# Prompt para mañana — refinamiento final pre-demo ChessQuery

Pegá esto en una sesión nueva de Claude Code abierta en
`/Users/agustingarridosnoopy/ChessQuery_FS3`.

---

## Contexto

Soy parte del equipo de ChessQuery (proyecto DSY1106 DuocUC, plataforma de
microservicios para ajedrez competitivo). La demo es en pocos días. Hoy
trabajamos durante varias horas refinando el sistema, pero **el tablero en
vivo sigue dando errores de respuesta intermitentes** y todavía hay items
del PLAN_DEMO sin verificar. Necesito que retomemos donde dejamos.

### Estado del código

- Rama actual: `main`. Hay **3 commits locales NO pusheados**: `c70f05f`
  (axios retry), `c0e2669` (gateway pool), `75bc89d` (perf+session fix).
- Spec `chessquery-pre-demo-refinement/requirements.md`: 19/20 R* hechos.
- Pendientes formales del spec: R3 (LAN magic link, investigación) y R19
  (dry-run cronometrado real).
- N1 (notificaciones inbox) y N2 (email transaccional) implementados.
- Hoy verifiqué end-to-end por curl: moves entre 34-96ms, resign 868ms,
  notificaciones llegan, emails llegan a Mailpit. **Pero en el browser
  durante una partida real el usuario reporta errores de respuesta que
  hay que reproducir y diagnosticar.**

### Arquitectura mínima

- Supabase local (`supabase start`) → Auth (54321), Studio (54323),
  Mailpit (54324), Postgres (54322), SMTP Inbucket (54325).
- 7 microservicios Java (ports 8080-8086) — gateway en 8080.
- 3 BFFs Node — bff-player en 3001.
- 2 frontends Vite — chess-portal en 5173 (PLAYER), organizer-panel en 5174.
- Stack levantado con `cd infrastructure && make up`.

### Credenciales de prueba (password único: `demo1234`)

| Email | Rol | player.id | Tab |
|---|---|---|---|
| `ana@demo.cl` | PLAYER | 4488 | http://127.0.0.1:5173 |
| `bruno@demo.cl` | PLAYER | 977 | http://127.0.0.1:5173 |
| `carla@demo.cl` | ORGANIZER | 978 | http://127.0.0.1:5174 |

Si los users no existen (post `make demo-reset` o `supabase stop && start`),
recreámelos vía Admin API (ver `docs/DEMO_FLOWS_VERIFICATION.md` o el
historial de chat anterior — se hace con curl + SERVICE_ROLE_KEY).

### Bugs/observaciones frescas

1. **🔴 URGENTE — tablero en vivo da errores de respuesta intermitentes.**
   - Reproducir: 2 tabs (Bruno + Ana), Bruno crea partida, Ana entra,
     jugar 4-6 movidas seguidas. Mirar la consola del browser de ambos.
   - Hipótesis vivas:
     - Connection pool del gateway tiene conexiones zombie hacia un BFF
       reiniciado (ya tiene `evictInBackground: 5s` y retry 1×).
     - El cliente axios reintenta una sola vez con 250ms de gap; si el
       backend tarda más de 250ms en aceptar nuevas connections, falla.
     - Realtime broadcast (Supabase) puede estar timing-out el thread
       pool de `LiveGameBroadcaster` y atorando moves nuevos.
     - El `SupabaseJwtAuthFilter` cae a HMAC fallback si el JWKS no está
       cacheado para un kid → puede causar 401 o 503 al validar JWT.
   - Acción primera: **levantar la app, jugar partida real, capturar
     consola del browser + `docker logs chessquery_api_gateway` +
     `docker logs chessquery_bff_player` + `docker logs chessquery_ms_game`
     simultáneamente.** Sin logs reproducidos, todo lo demás es teoría.

2. **`ms-etl` queda en `(unhealthy)` permanente** en `docker compose ps`.
   Distrae visualmente. Probablemente el healthcheck apunta a un endpoint
   que no existe.

3. **`demo-reset` no purga conflictos de `lichess_username`** ni resetea
   la sequence. IDs ya están en 4488 (de varios test runs). Crear users
   nuevos con un lichess que choque con un seed (ej. "DrNykterstein" =
   id=10) hace fallar el webhook silenciosamente.

4. **Frontend `.env` files están gitignored.** Tus compañeros van a
   clonar y faltarán. Crear `.env.example` adentro de cada app.

### Lo que está confirmado funcionando (NO romper)

- Login Supabase + JWT + resolución UUID→player.id en gateway.
- Cross-tab session isolation (storageKey único por tab en sessionStorage).
- Live game move/join/resign/draw/timeout endpoints (curl 34-96ms move).
- Notificaciones in-app (campana 🔔 con badge + dropdown).
- Email transaccional a Mailpit en `game.finished` (6 emails generados ok).
- ELO recalc tras `game.finished` vía RabbitMQ.
- Pre-moves, picker promoción Q/R/B/N, navegación historial ←→, oferta
  tablas, reloj funcional (R5/R9/R10/R11/R12) — todos en código pero
  algunos sin verificar visualmente.

## Plan ordenado (P0 primero)

### P0 — bloqueantes para demo

1. **Reproducir y arreglar errores del tablero (ver bug #1 arriba).**
   Acción: jugar partida real, capturar logs simultáneos, identificar el
   patrón. Si es connection reset → considerar subir el retry a 2 veces o
   meter circuit breaker. Si es 503 USER_NOT_RESOLVED → bug en cache. Si
   es timeout → revisar si Supabase Realtime broadcaster atora threads.
   *Esfuerzo: 1-2h dependiendo de la causa.*

2. **Warm-up del gateway al startup.** Hoy el primer load del dashboard
   tarda 1.9-3s en frío. Agregar `ApplicationReadyEvent` listener que
   pre-fetche JWKS (ya está) + pre-establezca 3-5 connections al pool de
   ms-users con un health-check `/users/by-supabase-id/...` dummy.
   *Esfuerzo: 30 min.*

3. **Fix `ms-etl` healthcheck unhealthy** (o sacarlo del `make up` con
   docker compose profiles). *Esfuerzo: 20 min.*

4. **`demo-reset` mejorado**: agregar
   `UPDATE player SET lichess_username = NULL WHERE id > 13` y
   `ALTER SEQUENCE player_id_seq RESTART WITH 14`. *Esfuerzo: 15 min.*

### P1 — flujos del PLAN_DEMO no verificados

5. **Flujo 2 (torneo end-to-end).** Carla (ORGANIZER) → crear "Demo Cup"
   SWISS → Ana+Bruno se inscriben → Carla confirma → genera pairings
   ronda 1. Verificar que `POST /api/organizer/tournaments` devuelva
   201 (no 403/400). Studio para ver fila en `tournament_db`.
   *Esfuerzo: 30 min + 30 min si hay bug de rol.*

6. **Flujo 3 (standings + ELO post-juego de torneo).** Depende del 5.
   *Esfuerzo: 20 min.*

7. **Flujo 4 (búsqueda fuzzy).** Tipear "Salin" → "Pablo Salinas". Verificar
   que el perfil público muestre `lichessUsername` (R6).
   *Esfuerzo: 5 min.*

### P2 — pulido visible

8. **Validación visual de live game** en browser de:
   reloj (R5), pre-moves (R9), picker promoción (R10), historial ←→ (R12),
   oferta tablas (R11), toasts (R8), material (R4), header ELO+bandera (R7).
   *Esfuerzo: 30 min.*

9. **Default time control 3 min + 0** al crear partida live (sin esto el
   reloj no se renderiza porque `timeControlInitialMs` es null).
   *Esfuerzo: 5 min — cambio en `LiveGame.tsx` o el botón de "Empezar".*

10. **`frontend/apps/*/​.env.example`** + nota en README.
    *Esfuerzo: 5 min.*

### P3 — quality-of-life para los compañeros

11. **`make demo-up`** que hace `up` + espera healthchecks +
    `seed-demo-users.sh` (crea ana/bruno/carla via Admin API, lo que hicimos
    a mano hoy). *Esfuerzo: 30 min.*

12. **README quickstart actualizado** para post-clone:
    pre-requisitos (Docker, supabase CLI, JDK 21, Node 20), comandos,
    URLs, credenciales. *Esfuerzo: 30 min.*

13. **(Opcional) R3 — magic link LAN.** Investigar las 3 hipótesis del
    `docs/MAGIC_LINK_LAN.md`. Si no resuelve, queda workaround "demo
    desde desktop". *Esfuerzo: 1-2h, sin garantía.*

14. **(Opcional) R19 — dry-run real cronometrado.** Plantilla en
    `docs/DEMO_DRYRUN.md`. *Esfuerzo: 30 min ejecución + 15 min ajustes.*

### Cuando esté todo verde

15. **Pushear los 3 commits locales pendientes** (`c70f05f`, `c0e2669`,
    `75bc89d`) + cualquier nuevo del trabajo de mañana. `git push origin main`.

## Comandos útiles para arrancar

```bash
# Levantar todo
open -a Docker  # esperar Docker daemon
supabase status || supabase start
cd /Users/agustingarridosnoopy/ChessQuery_FS3/infrastructure
make up         # tarda 1-2 min
docker compose ps  # esperar todos (healthy)

# Vite dev servers (background)
cd /Users/agustingarridosnoopy/ChessQuery_FS3/frontend/apps/chess-portal
npm run dev -- --host 0.0.0.0 --port 5173 > /tmp/chess-portal.log 2>&1 &
cd /Users/agustingarridosnoopy/ChessQuery_FS3/frontend/apps/organizer-panel
npm run dev -- --host 0.0.0.0 --port 5174 > /tmp/organizer-panel.log 2>&1 &

# Verificar healthchecks
for p in 8080 8081 8082 8083 8084 8085; do
  printf "%s: " $p
  curl -fs http://localhost:$p/actuator/health | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])"
done

# Si hay que recrear users de demo:
SUPA_URL="http://127.0.0.1:54321"
SERVICE_KEY=$(supabase status -o env | grep SERVICE_ROLE_KEY | cut -d'"' -f2)
# (Usar curl POST /auth/v1/admin/users con apikey + Authorization Bearer service_key)
# Ver historial o docs/DEMO_FLOWS_VERIFICATION.md
```

## Archivos críticos para recordar

- `api-gateway/src/main/java/cl/chessquery/gateway/filter/SupabaseJwtAuthFilter.java`
  — JWKS cache + UUID resolution
- `api-gateway/src/main/java/cl/chessquery/gateway/auth/PlayerIdResolver.java`
  — WebClient con pool propio + Caffeine cache
- `frontend/packages/shared/src/api-client.ts` — axios con cache de token + retry
- `frontend/packages/shared/src/supabase.ts` — client con storageKey único por tab
- `frontend/apps/chess-portal/src/pages/LiveGame.tsx` — UI del tablero (~1100 LoC)
- `ms-game/src/main/java/cl/chessquery/game/service/LiveGameService.java`
  — backend del live game
- `ms-game/src/main/java/cl/chessquery/game/realtime/LiveGameBroadcaster.java`
  — Supabase Realtime broadcaster (fire-and-forget pool)
- `infrastructure/docker-compose.yml` — toda la infra
- `infrastructure/scripts/demo-reset.sh` — limpieza de datos demo
- `docs/PLAN_DEMO.md` — guion de la demo
- `docs/DEMO_DRYRUN.md` — plantilla cronometrada
- `docs/DEMO_FLOWS_VERIFICATION.md` — checklist de verificación

## Forma de trabajar que funcionó hoy

- Diagnosticar primero (logs reales, no teoría).
- Curl directo al ms-X para aislar gateway/bff/ms.
- TodoWrite para no perder el hilo cuando aparecen 3 bugs en paralelo.
- Commit local frecuente, push solo al final cuando esté validado.
- No reiniciar containers durante el testing en vivo (mata connections).

---

**Empezá por bug #1 (errores del tablero) — sin eso, todo lo demás es
secundario.** Después seguí el orden P0 → P1 → P2 → P3 y al cierre
pushea todo a main.
