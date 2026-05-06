# Demo — Tablero en vivo

MVP V1: 2 jugadores, ajedrez estándar, sin reloj, sin chat, sin spectators.

## Pre-requisitos

```bash
# Supabase Local corriendo + service key disponible
supabase start
supabase status   # toma URL, anon key, service key, JWT secret

# .env (infrastructure/.env)
SUPABASE_URL=http://host.docker.internal:54321
SUPABASE_JWT_SECRET=<jwt secret>
SUPABASE_SERVICE_KEY=<service_role key>     # ms-game lo necesita para broadcast
SUPABASE_WEBHOOK_SECRET=dev-webhook-secret
STORAGE_PROVIDER=supabase

# .env de cada frontend
VITE_SUPABASE_URL=http://127.0.0.1:54321
VITE_SUPABASE_ANON_KEY=<anon key>

# Stack docker
cd infrastructure && make up
# espera healthchecks
```

## Crear 2 cuentas de prueba

Vía Supabase Studio (http://127.0.0.1:54323 → Authentication → Users → Add user)
o via signup desde la UI:

- `alice@chessquery.test` / `alice-pass` → role=PLAYER (default)
- `bob@chessquery.test`   / `bob-pass`   → role=PLAYER

## Flujo demo

1. Abrir 2 navegadores distintos (o uno normal + uno incógnito).
2. **Navegador A** → http://localhost:5173 → login `alice@…`.
3. Sidebar → **Jugar** → botón **♞ Empezar partida en vivo**.
4. Te redirige a `/play/<id>`. Aparece tablero, te toca mover blancas.
5. Copiá la URL de la card de la derecha.
6. **Navegador B** → login `bob@…` → pegá la URL.
7. Bob entra automáticamente como negras (auto-join).
8. Juegan: cada jugada se valida en backend (chesslib) y se propaga
   por Supabase Realtime al rival.
9. Al producirse mate / stalemate / 50-move / repetición, la sesión
   queda FINISHED y se materializa en la tabla `game` con su PGN
   subido a Supabase Storage.
10. La card "Partida guardada como #N" linkea al perfil; desde ahí
    se puede pedir el signed URL del PGN.

## Smoke test automático

```bash
export SUPABASE_URL=http://127.0.0.1:54321
export SUPABASE_ANON_KEY=<anon>
export WHITE_EMAIL=alice@chessquery.test
export WHITE_PASSWORD=alice-pass
export BLACK_EMAIL=bob@chessquery.test
export BLACK_PASSWORD=bob-pass
./scripts/e2e/live_game_smoke.sh
```

Ejecuta Scholar's Mate (1.e4 e5 2.Bc4 Nc6 3.Qh5 Nf6 4.Qxf7#) entre
las 2 cuentas y verifica `status=FINISHED, result=1-0,
endReason=CHECKMATE, finalizedGameId>0`.

## Limitaciones conocidas V1

- Sin reloj (las columnas existen, sólo falta el handler de tiempo).
- Promoción siempre a dama (no hay UI de under-promotion).
- Sin oferta/aceptación de tablas (sólo resignar).
- Sin reconexión inteligente: el cliente recarga vía GET `/play/live/{id}`
  cuando llega un evento Realtime; si pierde la conexión a Realtime,
  basta refrescar la página.
- Matchmaking sigue siendo "compartir URL" — no hay queue real todavía.
- Sin spectators ni chat.

## Endpoints involucrados

| Acción | Endpoint (vía gateway) | Backend |
|---|---|---|
| Crear sesión | POST `/api/player/play/live` | ms-game `POST /games/live` |
| Ver estado | GET `/api/player/play/live/{id}` | ms-game `GET /games/live/{id}` |
| Sumarse | POST `/api/player/play/live/{id}/join` | ms-game `POST /games/live/{id}/join` |
| Mover | POST `/api/player/play/live/{id}/move` | ms-game `POST /games/live/{id}/move` |
| Rendirse | POST `/api/player/play/live/{id}/resign` | ms-game `POST /games/live/{id}/resign` |
| Realtime | canal `game:{id}` | broadcast desde ms-game |

## Archivos clave

- Backend: `ms-game/src/main/java/cl/chessquery/game/service/LiveGameService.java`
- Migración: `ms-game/src/main/resources/db/migration/V3__create_live_game.sql`
- Realtime: `ms-game/src/main/java/cl/chessquery/game/realtime/LiveGameBroadcaster.java`
- Frontend: `frontend/apps/chess-portal/src/pages/LiveGame.tsx`
