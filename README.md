# ChessQuery FS3

Plataforma de microservicios para ajedrez competitivo en Chile.
**Asignatura:** DSY1106 Desarrollo Fullstack III — DuocUC.
**Equipo:** Martín Mora, Agustín Garrido, Gabriel Espinoza.

> **Nota (2026-05):** stack migrado a Supabase Auth + Supabase Storage.
> MS-Auth, `auth_db` y MinIO removidos. Setup: [docs/SUPABASE_SETUP.md](./docs/SUPABASE_SETUP.md). El estado pre-migración es recuperable desde el commit `0fb84d5`.

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

> 📚 Índice completo de toda la documentación en [`docs/README.md`](./docs/README.md).

| Doc | Contenido | Indicador rúbrica |
|---|---|---|
| [`docs/ANALISIS_PATRONES.md`](./docs/ANALISIS_PATRONES.md) | 8 patrones diseño + 6 arquitectónicos + arquetipo Maven | 1, 2, 5, 6 |
| [`TESTING.md`](./TESTING.md) | Comandos para correr los 530 tests (Java + BFFs + Frontend) | 4, 8 |
| [`docs/PRUEBAS.md`](./docs/PRUEBAS.md) | Estrategia y detalle de pruebas unitarias e integración: qué cubre cada una, patrón `@SpringBootTest`+H2, cobertura JaCoCo y pasos futuros | 4, 8 |
| [`docs/repositorios.txt`](./docs/repositorios.txt) | Enlaces a c/repositorio con descripción | Requisito entrega |
| [`docs/CONTEXT.md`](./docs/CONTEXT.md) | ERD completo, contratos REST, eventos | — |
| [`docs/specs/HISTORIAS_USUARIO.md`](./docs/specs/HISTORIAS_USUARIO.md) | Historias de usuario | — |
| [`docs/specs/SPEC_LEY21719.md`](./docs/specs/SPEC_LEY21719.md) | Cumplimiento Ley 21.719 | — |
| [`infrastructure/aws/RUNBOOK_ECS.md`](./infrastructure/aws/RUNBOOK_ECS.md) | Despliegue AWS ECS Fargate paso a paso | — |
| [`docs/README-WINDOWS.md`](./docs/README-WINDOWS.md) | Setup Windows con setup.ps1 | — |
| [`docs/IMPLEMENTACION.md`](./docs/IMPLEMENTACION.md) | Documento de implementación: arquitectura general capa por capa | 1, 2 |
| [`docs/SECURITY_PLAN.md`](./docs/SECURITY_PLAN.md) | Auditoría de seguridad + plan de hardening (Supabase, gateway, MS) | — |
| [`docs/ORAL_DEFENSE_CHEAT_SHEET.md`](./docs/ORAL_DEFENSE_CHEAT_SHEET.md) | Argumentos clave para la defensa oral | 5, 6, 8 |
| [`docs/SELF_HOSTED_RUNNER.md`](./docs/SELF_HOSTED_RUNNER.md) | Configurar el runner self-hosted del CI | — |

## Desarrollo

Ver el README de cada componente para instrucciones específicas.
