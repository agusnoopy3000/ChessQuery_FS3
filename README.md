# ChessQuery FS3

Plataforma de microservicios para ajedrez competitivo en Chile.
**Asignatura:** DSY1106 Desarrollo Fullstack III — DuocUC.
**Equipo:** Martín Mora, Agustín Garrido, Gabriel Espinoza.

> **Nota (2026-05):** stack migrado a Supabase Auth + Supabase Storage.
> MS-Auth, `auth_db` y MinIO removidos. Setup: [docs/SUPABASE_SETUP.md](./docs/SUPABASE_SETUP.md). Rollback: [docs/ROLLBACK.md](./docs/ROLLBACK.md).

## Componentes

| Componente | Tipo | Puerto | Stack |
|---|---|---|---|
| Supabase Auth + Storage | externo | 54321 | Supabase Local |
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
# Desarrollo (todos los servicios)
cd infrastructure && make up

# Demo en MacBook Air M1 8GB (override JVM + apaga out-of-scope)
cd infrastructure && make demo-up

# 5 min antes de la demo: health check completo
make preflight
```

## Documentación clave (Parcial N°2)

| Doc | Contenido | Indicador rúbrica |
|---|---|---|
| [`docs/ANALISIS_PATRONES.md`](./docs/ANALISIS_PATRONES.md) | 8 patrones diseño + 6 arquitectónicos + arquetipo Maven | 1, 2, 5, 6 |
| [`docs/PLAN_BRANCHING.md`](./docs/PLAN_BRANCHING.md) | Estrategia branching + PRs + ejemplos conflictos | 3, 7 |
| [`docs/CHEATSHEET_DEFENSA.md`](./docs/CHEATSHEET_DEFENSA.md) | Distribución temas defensa por integrante | 5, 6, 7, 8 |
| [`repositorios.txt`](./repositorios.txt) | Enlaces a c/repositorio con descripción | Requisito entrega |
| [`CONTEXT.md`](./CONTEXT.md) | ERD completo, contratos REST, eventos | — |
| [`PLAN_DEMO.md`](./PLAN_DEMO.md) | Guion de la demo (4 flujos E2E) | — |
| [`docs/README-WINDOWS.md`](./docs/README-WINDOWS.md) | Setup Windows con setup.ps1 | — |
| [`docs/README-DEMO-M1.md`](./docs/README-DEMO-M1.md) | Demo en MacBook Air M1 8GB | — |

## Desarrollo

Ver el README de cada componente para instrucciones específicas.
