# api-gateway — Spring Cloud Gateway

Punto único de entrada al sistema. Enruta requests a los microservicios
correspondientes, valida JWT contra `ms-auth` y propaga claims al downstream.

- **Puerto:** 8080 (interno) — expuesto via Nginx :80
- **Stack:** Spring Cloud Gateway 2023.0.0

## Routing

| Path prefix | Target |
|---|---|
| `/auth/**` | `ms-auth:9090` |
| `/users/**` | `ms-users:8081` |
| `/tournaments/**` | `ms-tournament:8082` |
| `/games/**` | `ms-game:8083` |
| `/analytics/**` | `ms-analytics:8084` |
| `/notifications/**` | `ms-notifications:8085` |
| `/etl/**` | `ms-etl:8086` |

## Filtros aplicados

1. **JwtValidationFilter** — llama `GET ms-auth/auth/validate` y agrega:
   - `X-User-Id`
   - `X-User-Email`
   - `X-User-Role`
2. **RateLimiterFilter** (Redis) — 100 req/min por IP.

## Build & Run

```bash
mvn clean package
java -jar target/api-gateway-0.0.1-SNAPSHOT.jar
```
