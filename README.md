# ChessQuery FS3

Plataforma de microservicios para ajedrez competitivo en Chile.
**Asignatura:** DSY1106 Desarrollo Fullstack III — DuocUC.
**Equipo:** Martín Mora, Agustín Garrido, Gabriel Espinoza.

## Componentes

| Componente | Tipo | Puerto | Stack |
|---|---|---|---|
| [ms-auth](./ms-auth) | microservicio | 9090 | Spring Boot 3.2.4 |
| [ms-users](./ms-users) | microservicio | 8081 | Spring Boot 3.2.4 |
| [ms-tournament](./ms-tournament) | microservicio | 8082 | Spring Boot 3.2.4 |
| [ms-game](./ms-game) | microservicio | 8083 | Spring Boot 3.2.4 |
| [ms-analytics](./ms-analytics) | microservicio | 8084 | Spring Boot 3.2.4 |
| [ms-notifications](./ms-notifications) | microservicio | 8085 | Spring Boot 3.2.4 |
| [ms-etl](./ms-etl) | microservicio | 8086 | Python 3.11 + FastAPI |
| [api-gateway](./api-gateway) | gateway | 8080 | Spring Cloud Gateway |
| [bff-player](./bff-player) | BFF | 3001 | NestJS 10 |
| [bff-organizer](./bff-organizer) | BFF | 3002 | NestJS 10 |
| [bff-admin](./bff-admin) | BFF | 3003 | NestJS 10 |
| [frontend](./frontend) | monorepo NPM | 5173-5175 | React 18 + Vite |
| [archetypes/chessquery-ms-archetype](./archetypes/chessquery-ms-archetype) | arquetipo Maven | — | Maven 3.9 |

## Arrancar todo (Docker)

```bash
cd infrastructure
make up
```

## Documentación

- [`CONTEXT.md`](./CONTEXT.md) — ERD completo, contratos REST, eventos.
- [`docs/ANALISIS_PATRONES.md`](./docs/ANALISIS_PATRONES.md) — patrones de diseño y arquetipos.
- [`docs/PLAN_BRANCHING.md`](./docs/PLAN_BRANCHING.md) — estrategia de branching.
- [`HISTORIAS_USUARIO.md`](./HISTORIAS_USUARIO.md) — historias de usuario.
- [`PLAN_DEMO.md`](./PLAN_DEMO.md) — guion de la demo.

## Desarrollo

Ver el README de cada componente para instrucciones específicas.
