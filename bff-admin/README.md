# bff-admin — Backend For Frontend (Admin)

BFF NestJS para el panel administrativo. Foco en gestión del ciclo de
vida del ETL (AJEFECH, Lichess).

- **Puerto:** 3003
- **Stack:** NestJS 10

## Endpoints expuestos

| Método | Path | Descripción |
|---|---|---|
| GET  | `/health` | Health check |
| GET  | `/admin/dashboard` | KPIs agregados |
| GET  | `/admin/etl/status` | Estado de fuentes (FIDE, AJEFECH, CHESS_RESULTS, LICHESS) y circuit breakers |
| POST | `/admin/etl/sync/{source}` | Disparar sync manual (`source`: `fide` \| `ajefech` \| `chess_results` \| `lichess`) |
| GET  | `/admin/etl/logs?limit=` | Logs de runs |
| GET  | `/admin/users?q=` | Buscar usuarios para administración |

## Build & Run

```bash
npm install
npm run start:dev
```
