# ms-tournament — Servicio de Torneos

CRUD de torneos, generación de pairings (Suizo / Round-Robin / Knockout),
standings con desempate (Buchholz, Sonneborn-Berger).

- **Puerto:** 8082
- **DB:** `tournament_db` (PostgreSQL :5434)
- **Paquete:** `cl.chessquery.tournament`

## Patrones aplicados

- **Factory Method** — `PairingStrategyFactory.getStrategy(format)`
- **Strategy** — `PairingStrategy` con 3 implementaciones intercambiables
- **Circuit Breaker** (Resilience4j) — llamada a ms-users para ELO de seed,
  fallback ELO 1500

## Endpoints

| Método | Path | Auth |
|---|---|---|
| POST   | `/tournaments` | `X-User-Role: ORGANIZER` |
| GET    | `/tournaments?status=&format=&page=&size=` | público |
| GET    | `/tournaments/{id}` | público |
| PATCH  | `/tournaments/{id}/status` | ORGANIZER |
| POST   | `/tournaments/{id}/registrations` | PLAYER |
| GET    | `/tournaments/{id}/registrations` | ORGANIZER |
| POST   | `/tournaments/{id}/rounds/{n}` | ORGANIZER |
| GET    | `/tournaments/{id}/rounds/{n}` | público |
| PATCH  | `/tournaments/pairings/{id}/result` | ORGANIZER |
| GET    | `/tournaments/{id}/standings` | público |

## Eventos

Publica: `tournament.created`, `player.registered`, `tournament.round.starting`.

## Build & Run

```bash
mvn clean package
java -jar target/ms-tournament-0.0.1-SNAPSHOT.jar
```

## Tests

`SwissPairingStrategyTest` valida emparejamiento Suizo y desempates.

## Swagger

`http://localhost:8082/swagger-ui.html`
