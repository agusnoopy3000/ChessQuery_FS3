# ms-game — Servicio de Partidas

Registra partidas (casuales y de torneo + live games), calcula ELO (FIDE),
detecta apertura por PGN, persiste PGN en Supabase Storage.

- **Puerto:** 8083
- **DB:** `game_db` (PostgreSQL :5435)
- **Paquete:** `cl.chessquery.game`
- **Storage:** Supabase Storage bucket `chessquery-pgn` (legacy MinIO
  disponible vía `storage.provider=minio`)

## Lógica clave

- **ELO Calculator:** fórmula FIDE estándar. K=32 si `totalGames < 30`,
  K=16 en otro caso. Actualmente fijo en K=32 (integración con ms-analytics
  pendiente).
- **Opening Detector:** extrae primeros 10 movimientos del PGN y busca
  match por prefijo más largo en tabla `opening` (90+ aperturas ECO).
- **PGN Storage:** Cliente Supabase Storage REST. Key:
  `games/{year}/{month}/{gameId}.pgn`. URL presignada 1h.
- **Live Games:** sesiones WAITING → ACTIVE → FINISHED, validación de
  jugadas server-side, broadcasting via Supabase Realtime.
- **Invitaciones:** lookup de email vía ms-users + publica
  `game.invitation` para notif in-app.

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
