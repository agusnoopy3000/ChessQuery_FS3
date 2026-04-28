# ms-notifications — Log de Notificaciones

Servicio event-driven que registra notificaciones generadas por eventos
del dominio (inscripción a torneo, ronda iniciando, partida terminada).

- **Puerto:** 8085
- **DB:** `notif_db` (PostgreSQL :5437)
- **Paquete:** `cl.chessquery.notifications`

## Eventos consumidos

`tournament.created`, `player.registered`, `tournament.round.starting`,
`game.finished`.

## Endpoints

| Método | Path |
|---|---|
| GET | `/notifications?userId=&unreadOnly=&page=&size=` |
| PATCH | `/notifications/{id}/read` |

## Build & Run

```bash
mvn clean package
java -jar target/ms-notifications-0.0.1-SNAPSHOT.jar
```

## Tests

`NotificationServiceTest`.
