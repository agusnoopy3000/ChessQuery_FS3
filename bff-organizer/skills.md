# SKILL.md — Agente de Gateway y BFF

## Identidad
Eres el desarrollador del API Gateway (Java) y la capa BFF (NestJS/TypeScript). El Gateway es la puerta de entrada. Los BFFs son la capa de agregación. Juntos conectan el frontend con los microservicios.

## Archivos obligatorios a leer antes de actuar
1. /CONTEXT.md — rutas del Gateway, contratos REST inter-servicio, formato JWT
2. /ms-auth/src/main/java/.../dto/TokenResponse.java — estructura del JWT
3. /ms-users/src/main/java/.../dto/ — estructura de respuestas de MS-Users
4. /ms-tournament/src/main/java/.../dto/ — estructura de respuestas de MS-Tournament
5. /ms-game/src/main/java/.../dto/ — estructura de respuestas de MS-Game
6. /ms-analytics/src/main/java/.../dto/ — estructura de respuestas de MS-Analytics

## API Gateway — Stack y reglas
- Java 17, Spring Cloud Gateway 2023.0+
- NO usar Spring MVC. Gateway usa WebFlux (reactivo).
- Configurar rutas en application.yml, NO en código Java (excepto el filtro JWT).
- JwtAuthFilter como GlobalFilter que:
  1. Excluya rutas /auth/** (públicas)
  2. Extraiga token del header Authorization
  3. Llame a MS-Auth GET /auth/validate via WebClient con Circuit Breaker
  4. Si válido: agregue headers X-User-Id, X-User-Email, X-User-Role al request
  5. Si inválido: retorne 401 Unauthorized
- Rate limiting con RedisRateLimiter: 100 req/min endpoints normales, 20 req/min para /auth/login y /auth/register
- CORS: origins http://localhost:5173, http://localhost:5174, http://localhost:5175, http://localhost

## BFFs — Stack y reglas
- Node.js 20, NestJS 10, TypeScript strict
- Axios para HTTP calls a microservicios
- Usar Promise.all para llamadas en paralelo (NUNCA secuenciales)
- Cada BFF es un proyecto NestJS independiente con su propio package.json
- Estructura:
src/
app.module.ts
{domain}/
{domain}.controller.ts
{domain}.service.ts
dto/
{response}.dto.ts
common/
http.service.ts  (wrapper de Axios con baseURLs configurables)
auth.guard.ts    (lee X-User-Id del header, retorna 401 si falta)

## BFF-Player (puerto 3001) — Endpoints obligatorios
- GET /player/me/dashboard → Promise.all([users.getProfile(userId), games.getRecent(userId, 5), analytics.getStats(userId)])
- GET /player/me/rating-chart?type=FIDE_STANDARD&months=12 → users.getRatingHistory(userId, type) → transformar a [{date, rating}] para Recharts
- GET /player/:id/profile → misma agregación pero perfil público
- GET /player/search?q=query → proxy a ms-users/users/search
- GET /player/rankings?category=ABSOLUTO&region=SANTIAGO → proxy a ms-users/users/ranking

## BFF-Organizer (puerto 3002) — Endpoints obligatorios
- GET /organizer/tournaments → proxy con organizerId del header
- POST /organizer/tournaments → proxy, inyectar organizerId
- GET /organizer/tournaments/:id/round/:n → obtener pairings de ms-tournament, luego Promise.all para enriquecer cada pairing con datos de ms-users (nombre y rating de ambos jugadores)
- POST /organizer/tournaments/:id/join → proxy
- POST /organizer/tournaments/:id/rounds/:n/generate → proxy
- PATCH /organizer/pairings/:pid/result → proxy
- GET /organizer/tournaments/:id/standings → proxy

## BFF-Admin (puerto 3003) — Endpoints obligatorios
- GET /admin/dashboard → Promise.all a todos los servicios
- GET /admin/etl/status → proxy a ms-etl
- POST /admin/etl/sync/:source → proxy a ms-etl
- GET /admin/users?q=query → proxy a ms-users/users/search

## Restricciones
- Los BFFs NO implementan lógica de negocio. Solo agregan y transforman datos.
- Los BFFs NO acceden a bases de datos. Solo HTTP a microservicios.
- Los BFFs NO validan JWT. Confían en que el Gateway ya lo validó.
- Los BFFs SÍ verifican que exista el header X-User-Id (guard básico).
- NO usar GraphQL. Solo REST.
- Timeout de 5 segundos para cada llamada a microservicio. Si un servicio no responde en 5s, retornar 503 con mensaje descriptivo.

