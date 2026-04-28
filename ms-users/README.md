# ms-users — Servicio de Jugadores

Perfiles de jugadores, ELO, federación con AJEFECH/Lichess, búsqueda fuzzy.

- **Puerto:** 8081
- **DB:** `user_db` (PostgreSQL :5433) con extensión `pg_trgm`
- **Paquete:** `cl.chessquery.users`

## Endpoints

| Método | Path | Descripción |
|---|---|---|
| GET    | `/users` | Listado paginado |
| POST   | `/users/sync` | Sincroniza datos federados |
| GET    | `/users/{id}/profile` | Perfil completo (incluye `enrichmentSource`) |
| PUT    | `/users/{id}/profile` | Actualiza perfil |
| GET    | `/users/search?q=...` | Búsqueda fuzzy (pg_trgm) |
| GET    | `/users/{id}/rating-history` | Historial de ELO |
| GET    | `/users/ranking?category=SUB18` | Ranking nacional por categoría |
| PUT    | `/users/{id}/elo` | Actualiza ELO (consumido por ms-game) |
| GET    | `/users/{id}/elo` | ELO seed (consumido por ms-tournament) |

## Eventos RabbitMQ

- **Publica:** `user.registered`, `user.updated`
- **Consume:** `elo.updated` (cola dedicada `users.elo.queue`),
  `rating.updated` (federación)

## Build & Run

```bash
mvn clean package
java -jar target/ms-users-0.0.1-SNAPSHOT.jar
```

## Tests

```bash
mvn test
# AgeCategoryTest, PlayerSearchServiceTest, UserControllerIntegrationTest,
# RatingUpdatedConsumerTest
```

## Swagger

`http://localhost:8081/swagger-ui.html`
