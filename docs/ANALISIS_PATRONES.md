# Análisis de Patrones de Diseño y Arquetipos — ChessQuery

**Asignatura:** DSY1106 Desarrollo Fullstack III — DuocUC
**Equipo:** Martín Mora, Agustín Garrido, Gabriel Espinoza
**Evaluación:** Parcial N°2

---

## 1. Resumen ejecutivo

ChessQuery es una plataforma de gestión de ajedrez competitivo en Chile,
construida como un sistema **distribuido de microservicios** (7 MS Spring Boot
+ 3 BFFs NestJS + 1 ETL Python) acoplado a un **monorepo frontend** React/Vite
con librería compartida (`@chessquery/ui-lib`) consumida vía NPM workspaces.

Este documento justifica:

1. Los **patrones de diseño** aplicados en frontend y backend.
2. Los **patrones arquitectónicos** que estructuran el sistema.
3. El **arquetipo Maven** (`chessquery-ms-archetype`) que estandariza
   la creación de nuevos microservicios.

---

## 2. Patrones de Diseño implementados

### 2.1 Factory Method — `PairingStrategyFactory`

**Ubicación:** `ms-tournament/src/main/java/cl/chessquery/tournament/pairing/PairingStrategyFactory.java`

**Problema que resuelve:** Un torneo puede usar 3 formatos de emparejamiento
(Suizo, Round-Robin, Knockout). Sin Factory, cada controlador tendría que
hacer `if/switch` por formato y conocer todas las implementaciones. Esto
viola Open/Closed: agregar un formato nuevo requeriría modificar callers.

**Solución:**

```java
public PairingStrategy getStrategy(TournamentFormat format) {
    return switch (format) {
        case SWISS       -> swissStrategy;
        case ROUND_ROBIN -> roundRobinStrategy;
        case KNOCKOUT    -> knockoutStrategy;
    };
}
```

**Beneficio:** El servicio `RoundService` solicita la strategy al factory y
no conoce las implementaciones concretas. Agregar un nuevo formato (p.ej.
Sistema Acelerado) implica una clase nueva y un case nuevo, sin tocar
el resto del código.

### 2.2 Strategy — `PairingStrategy`

**Ubicación:** `ms-tournament/.../pairing/{Swiss,RoundRobin,Knockout}PairingStrategy.java`

**Problema que resuelve:** Cada algoritmo de emparejamiento tiene reglas muy
distintas (Suizo: por puntaje + Buchholz, Round-Robin: combinaciones fijas,
Knockout: árbol de eliminación). Un único método con condicionales sería
inmantenible y difícil de testear.

**Solución:** Interfaz común `PairingStrategy.generatePairings(round, players)`
con tres implementaciones intercambiables. Cada estrategia es testeable
de forma aislada (`SwissPairingStrategyTest`).

**Trade-off aceptado:** mayor número de clases a cambio de cohesión y
testabilidad. Para 3 algoritmos distintos el trade-off es claramente
favorable.

### 2.3 Circuit Breaker — Resilience4j

**Ubicación:** `ms-tournament/.../client/UsersServiceClient.java` (llamada a
`ms-users` para obtener ELO de un jugador en el seeding del torneo).

**Problema que resuelve:** Una caída momentánea de `ms-users` propagaría
fallos a todas las inscripciones de torneo, generando timeouts en cascada
y degradando la UX del organizador.

**Solución:**

```java
@CircuitBreaker(name = "usersService", fallbackMethod = "fallbackElo")
public Integer getPlayerElo(Long playerId) { ... }

private Integer fallbackElo(Long id, Throwable t) { return 1500; } // ELO neutro FIDE
```

**Beneficio:** Cuando ms-users falla, el circuito se abre tras N fallos
consecutivos y devuelve ELO 1500 sin esperar timeouts. El sistema mantiene
disponibilidad parcial y se recupera automáticamente.

### 2.4 Observer / Publish-Subscribe — RabbitMQ Topic Exchange

**Ubicación:** Exchange `ChessEvents` (topic), routing keys `user.*`,
`tournament.*`, `game.*`, `elo.*`, `rating.*`, `etl.*`.

**Problema que resuelve:** Cuando termina una partida, hay que actualizar
ELO en `ms-users`, recalcular stats en `ms-analytics`, notificar al jugador
en `ms-notifications` y actualizar el torneo en `ms-tournament`. Hacer esto
con llamadas REST síncronas crearía acoplamiento fuerte y un punto único
de fallo.

**Solución:** `ms-game` publica un evento `game.finished` + dos
`elo.updated` (uno por jugador). Cada servicio interesado se suscribe a
las routing keys que le importan, en su propia cola dedicada
(`users.elo.queue`, `analytics.events`, `notif.events`).

**Beneficio:** Acoplamiento débil, escalabilidad horizontal por consumer,
y agregar un nuevo suscriptor (p.ej. ms-rating) no requiere modificar
ms-game.

### 2.5 Repository — Spring Data JPA

**Ubicación:** Todos los microservicios Java (`*Repository extends JpaRepository`).

**Problema que resuelve:** Acceso a datos repetitivo (CRUD + queries),
acoplamiento entre servicio y SQL.

**Solución:** Interfaces `PlayerRepository`, `TournamentRepository`, etc.
que abstraen la persistencia. Spring genera implementaciones en runtime y
soporta `@Query` para casos complejos (búsqueda fuzzy con `pg_trgm`).

### 2.6 Backend For Frontend (BFF)

**Ubicación:** `bff-player/`, `bff-organizer/`, `bff-admin/` (NestJS).

**Problema que resuelve:** Cada portal (Player, Organizer, Admin) tiene
necesidades de datos diferentes. Si el frontend hablara directo con los MS,
generaría over-fetching, múltiples round-trips y lógica de composición
duplicada en el cliente.

**Solución:** Un BFF por audiencia. El BFF compone respuestas (player +
torneos + ELO) en una sola llamada, optimizada para la UI específica.

**Beneficio:** UX más rápida (una llamada vs N), evolución independiente
de cada portal, oportunidad de cache por audiencia.

### 2.7 Singleton — Spring Beans con scope `@Service`/`@Component`

Aplicado de forma transversal por el contenedor de Spring. Servicios como
`AuthService`, `EloCalculator` se instancian una sola vez por contexto.

---

## 3. Patrones Arquitectónicos

### 3.1 Microservicios + Database-per-Service

Cada MS posee su propia base PostgreSQL (`auth_db`, `user_db`,
`tournament_db`, `game_db`, `analytics_db`, `notif_db`, `etl_db`).

**Justificación:** Aislamiento de fallos, escalabilidad independiente,
libertad de modelo de datos por bounded context (DDD). Las referencias
cross-service (`player_id` en `tournament_db`) son `BIGINT` planos sin FK,
con integridad mantenida a nivel aplicación.

### 3.2 API Gateway

**Ubicación:** `api-gateway/` (Spring Cloud Gateway).

Punto único de entrada que:
- Enruta a microservicios por path (`/auth/**`, `/users/**`, ...).
- Llama a `ms-auth:9090/auth/validate` y propaga `X-User-Id`,
  `X-User-Role` a downstream — los MS confían en estos headers.
- Aplica rate-limiting (Redis, 100 req/min por IP).

**Beneficio:** Los microservicios no conocen JWT; toda autenticación
está centralizada.

### 3.3 Event-Driven Architecture (EDA)

Comunicación asíncrona vía RabbitMQ topic exchange. Eventos como contratos
versionados (`{ eventId, eventType, timestamp, payload }`).

**Justificación:** Desacopla productores y consumidores, habilita
event sourcing parcial (rating_history alimentado por eventos
`rating.updated`), permite agregar consumidores sin redeploy del productor.

### 3.4 JWT Stateless Authentication

**Ubicación:** `ms-auth/` con `jjwt 0.12.5`.

- Access token HS256, 15 min de vigencia.
- Refresh token almacenado como hash SHA-256 en BD, 30 días.
- BCrypt para passwords (cost factor 10).
- Soporte multi-dispositivo via tabla `refresh_token` con `device_id`.

### 3.5 Materialized View Pattern — `PLAYER_STATS_MV`

**Ubicación:** `analytics_db.player_stats_mv`.

Tabla pre-agregada mantenida por consumidores RabbitMQ. Evita JOINs
cross-DB (que serían imposibles en DB-per-service) y entrega stats en O(1)
al portal del jugador.

---

## 4. Arquetipo Maven — `chessquery-ms-archetype`

**Ubicación:** `archetypes/chessquery-ms-archetype/`.

### 4.1 Justificación

El equipo crea ~7 microservicios con stack idéntico. Sin un arquetipo:

- Cada MS reimplementa el `pom.xml` (riesgo de versiones inconsistentes
  de Spring Boot, Flyway, Lombok).
- Cada MS reimplementa el `Dockerfile` multi-stage.
- La estructura de paquetes diverge entre desarrolladores.
- El boilerplate (application.yml, Flyway init, springdoc) se copia
  a mano con errores.

### 4.2 Estandarización forzada

El arquetipo cristaliza decisiones técnicas:

| Decisión | Versión / Configuración |
|---|---|
| Java | 17 |
| Spring Boot | 3.2.4 |
| Spring Cloud | 2023.0.0 (Resilience4j) |
| Lombok | 1.18.30 (compatible Java ≤21) |
| Springdoc OpenAPI | 2.3.0 |
| JaCoCo | 0.8.11 |
| Base image runtime | `eclipse-temurin:17-jre-jammy` |

### 4.3 Uso

```bash
mvn archetype:generate \
  -DarchetypeGroupId=cl.chessquery.archetypes \
  -DarchetypeArtifactId=chessquery-ms-archetype \
  -DarchetypeVersion=1.0.0 \
  -DgroupId=cl.chessquery -DartifactId=ms-rating \
  -Dpackage=cl.chessquery.rating \
  -DserviceName=rating -DservicePort=8087 \
  -DdbName=rating_db -DdbPort=5439 \
  -DinteractiveMode=false
```

Genera un MS listo para `mvn spring-boot:run` y `docker build`,
con migraciones Flyway, conexión a RabbitMQ y Swagger UI funcionales.

---

## 5. Componente Frontend NPM — `@chessquery/ui-lib`

**Ubicación:** `frontend/packages/ui-lib/`.

Librería compartida (botones, tarjetas de jugador, tabla de standings)
consumida por las 3 apps (`chess-portal`, `organizer-panel`, `admin-panel`)
vía NPM workspaces (`"@chessquery/ui-lib": "workspace:*"`).

**Patrones aplicados en frontend:**

- **Component composition** (React) — los portales componen UI a partir
  de primitivas de `ui-lib`.
- **Custom Hooks** — `useAuth`, `usePlayers` encapsulan side-effects
  y comparten lógica entre componentes.
- **Adapter** — capa `api.ts` adapta respuestas del BFF al modelo del
  componente, aislando el cliente HTTP.

---

## 6. Tabla resumen — patrones × indicadores rúbrica

| Patrón | Tipo | Donde aplica | Indicador rúbrica |
|---|---|---|---|
| Factory Method | Diseño | ms-tournament | 1, 5 |
| Strategy | Diseño | ms-tournament | 1, 5 |
| Circuit Breaker | Diseño / Resiliencia | ms-tournament → ms-users | 1, 5 |
| Observer / Pub-Sub | Diseño / Integración | RabbitMQ ChessEvents | 1, 5 |
| Repository | Diseño | Todos los MS | 1, 5 |
| BFF | Arquitectónico | bff-player/organizer/admin | 2, 6 |
| API Gateway | Arquitectónico | api-gateway | 2, 6 |
| Microservices + DB-per-service | Arquitectónico | Todo el sistema | 2, 6 |
| Event-Driven Architecture | Arquitectónico | RabbitMQ | 2, 6 |
| JWT Stateless | Arquitectónico / Seguridad | ms-auth | 2, 6 |

---

## 7. Conclusiones

El proyecto aplica **5 patrones de diseño** + **5 patrones arquitectónicos**
integrados de forma coherente, cada uno justificado por un problema concreto
del dominio (no aplicados por moda). El arquetipo Maven asegura que la
estandarización sobreviva al crecimiento del equipo y al ingreso de nuevos
microservicios.
