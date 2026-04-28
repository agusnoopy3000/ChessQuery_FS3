# ms-game — Servicio de Partidas

Registra partidas, calcula ELO (FIDE), detecta apertura por PGN, sube PGN a MinIO/S3.

- **Puerto:** 8083
- **DB:** `game_db` (PostgreSQL :5435)
- **Paquete:** `cl.chessquery.game`
- **Storage:** MinIO bucket `chessquery-pgn`

## Lógica clave

- **ELO Calculator:** fórmula FIDE estándar. K=32 si `totalGames < 30`,
  K=16 en otro caso. Actualmente fijo en K=32 (integración con ms-analytics
  pendiente).
- **Opening Detector:** extrae primeros 10 movimientos del PGN y busca
  match por prefijo más largo en tabla `opening` (90+ aperturas ECO).
- **PGN Storage:** AWS SDK v2 con `path-style-access` para MinIO local.
  Key: `games/{year}/{month}/{gameId}.pgn`. URL presignada 1h.

## Endpoints

| Método | Path |
|---|---|
| POST | `/games` |
| GET  | `/games/{id}` |
| GET  | `/games?playerId=&gameType=&result=&page=&size=` |
| GET  | `/games/{id}/pgn-url` |

## Eventos

Publica al finalizar partida:
- `game.finished`
- `elo.updated` (×2, uno por jugador)

## Build & Run

```bash
mvn clean package
java -jar target/ms-game-0.0.1-SNAPSHOT.jar
```

## Tests

`EloCalculatorTest` cubre la fórmula FIDE y el ajuste de K.

## Swagger

`http://localhost:8083/swagger-ui.html`
