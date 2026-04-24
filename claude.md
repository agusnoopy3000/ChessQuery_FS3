# ChessQuery — Instrucciones

## Proyecto
ChessQuery es una plataforma de microservicios para ajedrez
competitivo en Chile. Curso DSY1106 DuocUC.

## Reglas globales que aplican a TODOS los módulos

### Antes de escribir código
1. Lee CONTEXT.md en la raíz del proyecto para entender los contratos
2. Lee SKILL.md en la carpeta actual para entender tu alcance
3. Si tu SKILL.md referencia archivos de otros módulos (DTOs, configs),
   léelos para conocer los contratos que consumes

### Convenciones de código
- Java: camelCase campos, PascalCase clases, paquete cl.chessquery.{servicio}
- TypeScript: camelCase todo, interfaces con PascalCase
- Python: snake_case campos/funciones, PascalCase clases
- SQL: snake_case tablas y columnas
- JSON en APIs REST: camelCase siempre
- Commits: conventional commits (feat:, fix:, docs:, refactor:)

### Formato de respuesta REST (todos los servicios)
Éxito: POST→201, GET→200, PUT/PATCH→200, DELETE→204
Error: { status, error, message, timestamp }
Paginación: { content, page, size, totalElements, totalPages }

### Docker
- Cada servicio tiene su Dockerfile en su raíz
- Base images: eclipse-temurin:17-jre-alpine (Java),
  node:20-alpine (NestJS), python:3.11-slim (ETL)
- Multi-stage build para Java: builder con Maven, runner con JRE
- Exponer solo el puerto del servicio, no puertos de debug

### Testing
- Mínimo 60% cobertura por módulo
- Tests en src/test/ (Java), src/**/*.spec.ts (NestJS),
  tests/ (Python)
- Usar H2 en memoria para tests de integración Java
- NO mockear bases de datos en tests de integración,
  usar testcontainers o H2

### Seguridad
- Nunca loguear passwords, tokens ni datos sensibles (PII)
- Nunca hardcodear secrets en código. Usar variables de entorno.
- JWT secret, passwords de BD, API keys van en .env
- .env va en .gitignore. Solo .env.example se commitea.

### Qué NO hacer (aplica a todos los agentes)
- NO acceder directamente a bases de datos de otros microservicios
- NO implementar autenticación en servicios que no son MS-Auth
  ni API Gateway. Confiar en headers X-User-Id, X-User-Role
- NO inventar endpoints que no están en CONTEXT.md ni en SKILL.md
- NO cambiar puertos asignados en CONTEXT.md
- NO usar librerías que no están en el SKILL.md del módulo actual
- NO generar código sin antes verificar que respeta los contratos
  de eventos y APIs definidos en CONTEXT.md
