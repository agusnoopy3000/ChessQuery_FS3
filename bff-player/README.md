# bff-player — Backend For Frontend (Player)

BFF NestJS para el portal del jugador. Compone respuestas combinando
ms-users, ms-tournament, ms-game y ms-analytics en una sola llamada
optimizada para la UI.

- **Puerto:** 3001
- **Stack:** NestJS 10 + Axios

## Endpoints expuestos

| Método | Path | Descripción |
|---|---|---|
| GET  | `/health` | Health check |
| GET  | `/player/dashboard?userId=` | Dashboard agregado (perfil + ratings + torneos activos + últimas partidas) |
| GET  | `/player/players?q=&page=&size=` | Búsqueda nacional |
| GET  | `/player/tournaments` | Torneos abiertos |
| POST | `/player/tournaments/{id}/register` | Inscripción |
| POST | `/player/sync/me` | Reenriquecer perfil propio (AJEFECH/Lichess) |

## Variables de entorno

| Var | Default |
|---|---|
| `PORT` | 3001 |
| `MS_USERS_URL` | http://localhost:8081 |
| `MS_TOURNAMENT_URL` | http://localhost:8082 |
| `MS_GAME_URL` | http://localhost:8083 |
| `MS_ANALYTICS_URL` | http://localhost:8084 |

## Build & Run

```bash
npm install
npm run start:dev
```

## Docker

```bash
docker build -t bff-player:latest .
```
