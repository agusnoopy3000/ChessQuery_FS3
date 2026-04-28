# bff-organizer — Backend For Frontend (Organizer)

BFF NestJS para el panel del organizador.

- **Puerto:** 3002
- **Stack:** NestJS 10

## Endpoints expuestos

| Método | Path | Descripción |
|---|---|---|
| GET  | `/health` | Health check |
| GET  | `/organizer/players?q=` | Búsqueda de jugadores |
| GET  | `/organizer/tournaments` | Torneos del organizador |
| POST | `/organizer/tournaments` | Crear torneo |
| PATCH | `/organizer/tournaments/{id}/status` | Cambiar estado |
| GET  | `/organizer/tournaments/{id}/registrations` | Listar inscripciones |
| PATCH | `/organizer/tournaments/{id}/registrations/{rid}` | Aprobar/rechazar inscripción |
| POST | `/organizer/tournaments/{id}/rounds/{n}` | Generar pairings |
| PATCH | `/organizer/pairings/{id}/result` | Cargar resultado |

## Build & Run

```bash
npm install
npm run start:dev
```
