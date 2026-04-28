# ms-analytics — Servicio de Estadísticas

Consume eventos de partida y mantiene una tabla pre-agregada
`PLAYER_STATS_MV` que evita JOINs cross-DB.

- **Puerto:** 8084
- **DB:** `analytics_db` (PostgreSQL :5436)
- **Paquete:** `cl.chessquery.analytics`

## Eventos

Consume:
- `game.finished` → actualiza wins/draws/losses, total_games, último game
- `elo.updated` → snapshot ELO para histórico

## Endpoints

| Método | Path | Descripción |
|---|---|---|
| GET | `/analytics/players/{id}/stats` | Stats agregados de un jugador |
| GET | `/analytics/players/{id}/games-summary` | Resumen partidas |

## Build & Run

```bash
mvn clean package
java -jar target/ms-analytics-0.0.1-SNAPSHOT.jar
```

## Tests

`GameEventsConsumerTest` valida la actualización idempotente del MV.
