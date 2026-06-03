# ChessQuery — Endpoints para QA

> Inventario de endpoints **probables desde internet** vía el ALB. Todos pasan por
> la cadena **Gateway → BFF → microservicio**. Verificado e2e el 2026-06-03 sobre
> la release v0.2.0 (task-def rev 7).

## Puntos de entrada (URLs estables)

| Qué | URL |
|---|---|
| Front jugador (chess-portal) | http://chessquery-chess-portal.s3-website-us-east-1.amazonaws.com |
| Front organizador (organizer-panel) | http://chessquery-organizer-panel.s3-website-us-east-1.amazonaws.com |
| **API (gateway vía ALB)** | `http://chessquery-alb-984810293.us-east-1.elb.amazonaws.com` |
| Supabase (Auth) | `https://pmtxxzscpactsgkijpul.supabase.co` |

A lo largo del doc: `ALB = http://chessquery-alb-984810293.us-east-1.elb.amazonaws.com`.

---

## 0. Cómo obtener un token (necesario para casi todo)

Todas las rutas `/api/player/**` y `/api/organizer/**` requieren
`Authorization: Bearer <token>` (JWT **ES256** de Supabase). Para QA:

```bash
SUPA=https://pmtxxzscpactsgkijpul.supabase.co
ANON=<VITE_SUPABASE_ANON_KEY de frontend/apps/*/.env.production>
ALB=http://chessquery-alb-984810293.us-east-1.elb.amazonaws.com

# Registro (crea cuenta + perfil vía webhook). role: PLAYER u ORGANIZER
curl -s -X POST "$SUPA/auth/v1/signup" -H "apikey: $ANON" -H "Content-Type: application/json" \
  -d '{"email":"qa1@chessquery-test.com","password":"Qa!12345","data":{"display_name":"QA Uno","role":"PLAYER"}}'

# Login → token
TOKEN=$(curl -s -X POST "$SUPA/auth/v1/token?grant_type=password" -H "apikey: $ANON" \
  -H "Content-Type: application/json" \
  -d '{"email":"qa1@chessquery-test.com","password":"Qa!12345"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")

# Llamada autenticada
curl -s -H "Authorization: Bearer $TOKEN" "$ALB/api/player/rankings"
```

> El rol (PLAYER/ORGANIZER) va en `data.role` al registrarse. Las rutas
> `/api/organizer/**` requieren rol ORGANIZER.

---

## 1. Salud / públicos (sin token)

| Método | Ruta | Propósito |
|---|---|---|
| GET | `$ALB/actuator/health` | Salud global del gateway |
| GET | `$ALB/actuator/health/readiness` | Readiness (lo usa el ALB) → `{"status":"UP"}` |
| GET | `$ALB/actuator/health/liveness` | Liveness |

---

## 2. Jugador — `/api/player/**` (token PLAYER) → bff-player

| Método | Ruta | Microservicio | Propósito |
|---|---|---|---|
| GET | `/api/player/me/dashboard` | ms-users | Perfil + stats del usuario logueado |
| GET | `/api/player/me/rating-chart` | ms-analytics | Serie histórica de ELO |
| GET | `/api/player/rankings` | ms-analytics/ms-users | Ranking nacional |
| GET | `/api/player/search?q=<texto>` | ms-users | Buscar jugadores |
| GET | `/api/player/:id/profile` | ms-users | Perfil público de un jugador |
| GET | `/api/player/:id/lichess` | ms-users | Datos enriquecidos de Lichess |
| **Partidas en vivo (ms-game)** ||||
| POST | `/api/player/play/find-match` | ms-game | Buscar emparejamiento casual |
| POST | `/api/player/play/games` | ms-game | Registrar partida |
| POST | `/api/player/play/live` | ms-game | Crear partida en vivo |
| GET | `/api/player/play/live/:id` | ms-game | Estado de una partida en vivo |
| POST | `/api/player/play/live/:id/join` | ms-game | Unirse a partida |
| POST | `/api/player/play/live/:id/move` | ms-game | Enviar jugada |
| POST | `/api/player/play/live/:id/resign` | ms-game | Rendirse |
| POST | `/api/player/play/live/:id/draw` | ms-game | Ofrecer/aceptar tablas |
| POST | `/api/player/play/live/:id/timeout` | ms-game | Marcar caída por tiempo |
| POST | `/api/player/play/live/:id/rematch` | ms-game | Revancha |
| POST | `/api/player/play/live/:id/invite` | ms-game | Invitar por email (in-app + link, **no crea cuenta**) |
| **Notificaciones (ms-notifications)** ||||
| GET | `/api/player/notifications` | ms-notifications | Listar notificaciones |
| GET | `/api/player/notifications/unread-count` | ms-notifications | Contador no leídas |
| POST | `/api/player/notifications/:id/read` | ms-notifications | Marcar una como leída |
| POST | `/api/player/notifications/read-all` | ms-notifications | Marcar todas leídas |
| **Torneos (ms-tournament)** ||||
| GET | `/api/player/tournaments` | ms-tournament | Torneos disponibles |
| GET | `/api/player/tournaments/:id` | ms-tournament | Detalle de torneo |
| GET | `/api/player/tournaments/:id/standings` | ms-tournament | Clasificación |
| GET | `/api/player/tournaments/:id/round/:n` | ms-tournament | Emparejamientos de la ronda n |
| GET | `/api/player/tournaments/:id/my-registration` | ms-tournament | Mi inscripción |
| POST | `/api/player/tournaments/:id/register` | ms-tournament | Inscribirse |
| POST | `/api/player/sync` | ms-users | Sincroniza perfil tras login (fallback del webhook) |

---

## 3. Organizador — `/api/organizer/**` (token ORGANIZER) → bff-organizer

| Método | Ruta | Microservicio | Propósito |
|---|---|---|---|
| GET | `/api/organizer/tournaments` | ms-tournament | Mis torneos |
| GET | `/api/organizer/tournaments/:id` | ms-tournament | Detalle |
| POST | `/api/organizer/tournaments` | ms-tournament | Crear torneo |
| DELETE | `/api/organizer/tournaments/:id` | ms-tournament | Eliminar torneo |
| PATCH | `/api/organizer/tournaments/:id/status` | ms-tournament | Cambiar estado (OPEN/IN_PROGRESS/...) |
| GET | `/api/organizer/tournaments/:id/round/:n` | ms-tournament | Emparejamientos de la ronda |
| POST | `/api/organizer/tournaments/:id/rounds/:n/generate` | ms-tournament + ms-game | **Generar ronda** → crea partidas en vivo + notifica |
| POST | `/api/organizer/tournaments/:id/join` | ms-tournament | Unir jugador al torneo |
| PATCH | `/api/organizer/pairings/:pid/result` | ms-tournament | Cargar resultado de un emparejamiento |
| GET | `/api/organizer/tournaments/:id/standings` | ms-tournament | Clasificación |
| GET | `/api/organizer/tournaments/:id/registrations` | ms-tournament | Inscripciones |
| PATCH | `/api/organizer/registrations/:rid/approve` | ms-tournament | Aprobar inscripción |
| PATCH | `/api/organizer/registrations/:rid/reject` | ms-tournament | Rechazar inscripción |
| GET | `/api/organizer/notifications` | ms-notifications | Notificaciones |
| GET | `/api/organizer/notifications/unread-count` | ms-notifications | Contador no leídas |
| POST | `/api/organizer/notifications/:id/read` | ms-notifications | Marcar leída |
| POST | `/api/organizer/notifications/read-all` | ms-notifications | Marcar todas leídas |

---

## 4. Auth (Supabase, directo — no por el gateway)

| Método | Ruta | Propósito |
|---|---|---|
| POST | `$SUPA/auth/v1/signup` | Registro |
| POST | `$SUPA/auth/v1/token?grant_type=password` | Login |
| POST | `$SUPA/auth/v1/token?grant_type=refresh_token` | Refrescar token |
| POST | `$SUPA/auth/v1/recover` | Recuperar contraseña (envía email; rate-limit con SMTP por defecto) |
| POST | `$SUPA/auth/v1/logout` | Cerrar sesión |

Todas requieren header `apikey: <ANON>`.

---

## 5. No disponibles en este entorno

| Ruta | Motivo |
|---|---|
| `/api/admin/**` (dashboard, etl/status, etl/logs, etl/sync/:source, users) | **bff-admin / ms-etl no están desplegados** en la task ECS (fuera de alcance de la demo). Documentados para completitud. |

---

## Notas para QA

- **Tiempo real de partidas**: además de la API, el front se suscribe a canales
  Supabase Realtime (`game:{id}`) para el tablero en vivo.
- **Flujo torneo en vivo**: generar ronda crea las partidas y notifica a los jugadores;
  al terminar una partida el resultado **vuelve solo** al pairing y la clasificación
  (RabbitMQ `game.finished` → ms-tournament). La grilla del organizador auto-refresca cada 8s.
- **Sin HTTPS** (HTTP plano por ALB/S3) — esperado en la demo; los navegadores pueden
  advertir sobre formularios en HTTP.
- Reportar hallazgos contra esta lista (método + ruta + payload + respuesta esperada vs real).
