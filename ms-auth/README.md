# ms-auth — Servicio de Autenticación

JWT issuance, refresh tokens y validación. Spring Boot 3.2.4 sobre Java 17.

- **Puerto:** 9090
- **DB:** `auth_db` (PostgreSQL :5432)
- **Paquete:** `cl.chessquery.auth`

## Build & Run

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/ms-21.0.8/Contents/Home"
mvn clean package
java -jar target/ms-auth-0.0.1-SNAPSHOT.jar
```

## Docker

```bash
docker build -t ms-auth:latest .
# o desde infrastructure/: make up
```

## Endpoints

| Método | Path | Descripción |
|---|---|---|
| POST | `/auth/register` | Crea usuario (BCrypt password) |
| POST | `/auth/login` | Devuelve `accessToken` + `refreshToken` |
| POST | `/auth/refresh` | Renueva access token |
| POST | `/auth/logout` | Revoca refresh token |
| GET  | `/auth/validate` | Valida JWT (usado por API Gateway) |

## Seguridad

- HS256 con `jjwt 0.12.5`. Access token 15 min, refresh 30 días.
- Refresh tokens guardados como **SHA-256 hash** (no plaintext).
- BCrypt cost 10 para passwords.
- `SecurityConfig` permite todos los endpoints; la validación JWT es
  programática vía `JwtService.validate()`.

## Swagger

`http://localhost:9090/swagger-ui.html`

## Tests

```bash
mvn test
```
