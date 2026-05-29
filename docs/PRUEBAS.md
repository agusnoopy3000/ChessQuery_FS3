# Pruebas de ChessQuery — Unitarias e Integración (contexto y detalle)

Este documento explica **qué prueban**, **cómo están construidas** y **qué cubren** las suites de
test del proyecto, para que cualquiera que retome el desarrollo entienda el porqué de cada pieza.

> **Relación con otros docs:** `TESTING.md` (raíz) es la guía *operativa* — comandos para correr
> todo tras un `git clone`. Este documento es el *de fondo*: la teoría, el diseño y la cobertura.
> Si solo querés ejecutar los tests, andá a `TESTING.md`.

Última verificación de la suite: **555 tests, 0 fallos, 0 errores** (corrida local sobre Arch Linux,
Maven 3.9.16 + JDK 17 + Node 26).

---

## 1. Panorama general

| Capa | Servicio | Framework | Tests | Tipo |
|---|---|---|---:|---|
| Backend Java | `api-gateway` | JUnit 5 + Mockito + WebTestClient | 31 | Unit + slice de filtro |
| Backend Java | `ms-users` | JUnit 5 + Mockito + `@SpringBootTest` | 110 | Unit + Integración H2 |
| Backend Java | `ms-tournament` | JUnit 5 + Mockito + `@SpringBootTest` | 94 | Unit + Integración H2 |
| Backend Java | `ms-game` | JUnit 5 + Mockito + `@SpringBootTest` | 91 | Unit + Integración H2 |
| Backend Java | `ms-notifications` | JUnit 5 + Mockito + `@SpringBootTest` | 65 | Unit + Integración H2 |
| Backend Java | `ms-analytics` | JUnit 5 + Mockito + `@SpringBootTest` | 14 | Unit + Integración H2 |
| BFF | `bff-player` | Jest (NestJS) | 47 | Unit (service + http) |
| BFF | `bff-organizer` | Jest (NestJS) | 28 | Unit (service + http) |
| Frontend | `chess-portal` | Vitest + RTL + jsdom | 37 | Component/page specs |
| Frontend | `organizer-panel` | Vitest + RTL + jsdom | 38 | Component/page specs |
| | | **TOTAL** | **555** | |

**Pirámide de pruebas aplicada:** base ancha de **unitarias** rápidas (mockean dependencias),
una capa de **integración** por microservicio que valida el wiring real HTTP→Service→JPA contra
una base de datos en memoria, y la validación **E2E** queda en manual con Docker Compose
(ver `TESTING.md` §4) — no automatizada, por costo de infraestructura.

---

## 2. Pruebas unitarias

### 2.1 Qué son aquí

Verifican **una unidad** (servicio, controller, consumer, filtro, estrategia) con **todas sus
dependencias mockeadas**. No tocan red, broker ni base de datos. Son las más numerosas y rápidas.

- **Java:** JUnit 5 + Mockito. Los controllers se prueban como *slice* con `@WebMvcTest` (levanta
  solo la capa web, mockea el service con `@MockBean`). Los servicios se prueban con Mockito puro.
- **BFF (NestJS):** Jest con mocks de los HTTP clients.
- **Frontend:** Vitest + React Testing Library; se mockean `@tanstack/react-query`,
  `@chessquery/ui-lib` y el módulo `../api`.

### 2.2 Cobertura por módulo Java

**`api-gateway` (31 tests)** — el borde de seguridad del sistema:
- `SupabaseJwtAuthFilterTest` — filtro JWT: rutas públicas (`/actuator`), `extractRole` (claim
  directo, `app_metadata.role`, fallback `PLAYER`), propagación de `provisionClaims`
  (firstName/lastName/lichessUsername/clubName del `user_metadata`), validación de tokens HS256
  reales y branch de `kid` desconocido en el cache JWKS → fallback HMAC + refresh en background.
- `PlayerIdResolverTest` — resolución `UUID Supabase → player.id` vía WebClient (stub).
- `SupabaseWebhookControllerTest` — endpoint `user-registered`.
- `SupabaseAuthHealthIndicatorTest` — health del proveedor de auth.

**`ms-users` (110 tests)** — dominio de jugadores y ranking:
- `PlayerServiceTest` — sync/provision/profile/update/elo/search/history.
- `RankingServiceTest`, `PlayerSearchServiceTest`, `AgeCategoryTest` (lógica de categorías por edad).
- `messaging/*ConsumerTest` — `EloUpdatedConsumer`, `RatingUpdatedConsumer`, `UserRegisteredConsumer`.
- `UserControllerTest` — slice `@WebMvcTest`.
- `EventPublisherServiceTest` — publicación de eventos a RabbitMQ (mock del template).

**`ms-tournament` (94 tests)** — torneos y emparejamientos:
- `TournamentServiceTest` — ciclo de vida del torneo (28 casos).
- `pairing/PairingStrategiesTest` + `SwissPairingStrategyTest` — estrategias Knockout, RoundRobin,
  Swiss y la Factory.
- `UserEloClientTest` — cliente HTTP con **circuit breaker**.
- `TournamentControllerTest` — slice `@WebMvcTest` (18 casos).

**`ms-game` (91 tests)** — el corazón con la lógica de ajedrez:
- `LiveGameServiceTest` — **el más rico** (33 casos). Cubre `detectTerminal` en todas sus ramas:
  CHECKMATE (Fool's Mate `1.f3 e5 2.g4 Qh4#`), STALEMATE, DRAW_INSUFFICIENT (K+B vs K),
  DRAW_50MOVE (contador halfmove=99). Incluye lock pesimista en `move()`.
- `EloCalculatorTest` — cálculo de ELO post-partida.
- `OpeningDetectorTest` — detección de apertura a partir de las jugadas.
- `controller/GameControllerTest`, `LiveGameControllerTest` — slices `@WebMvcTest`.
- `realtime/LiveGameBroadcasterTest`, `service/LiveGameFinalizerTest`.
- `storage/*` — `MinioStorageServiceTest`, `SupabaseStorageServiceTest`,
  `SupabaseStorageHealthIndicatorTest` (subida del PGN).

**`ms-notifications` (65 tests)** — fan-out de eventos a notificaciones:
- `ConsumersTest` — los 3 consumers (Tournament/Game/Etl events) con todos sus routings y el
  manejo de idempotencia: `alreadyProcessed → ack` sin re-despachar,
  `DataIntegrityViolation → ack` (carrera de idempotencia), `exception → nack` sin requeue.
- `NotificationServiceTest`, `PlayerNameResolverTest` (cache + fallback), `MockEmailServiceTest`.
- `NotificationControllerTest` — slice `@WebMvcTest`.

**`ms-analytics` (14 tests)** — agregaciones:
- `GameEventsConsumerTest` — draws, running average, best ELO, idempotencia y eventos desconocidos.

### 2.3 Cobertura por suite JS

- **`bff-player` (47)**: `player.service.spec` + `common/http.service.spec` (retry transitorio +
  traducción de errores del backend).
- **`bff-organizer` (28)**: `organizer.service.spec` + `common/http.service.spec`.
- **`chess-portal` (37)**: specs de `Home`, `Login`, `Register`, `MyDashboard`,
  `PlayerMatchmaking`, `PlayerPortal`, `TournamentDetail` — cada una cubre loading, error,
  render del happy-path y navegación.
- **`organizer-panel` (38)**: `Login`, `OrganizerPortal`, `OrganizerTournaments` (esta última
  ampliada a 28 casos: listado/KPIs, búsqueda, filtros por estado, selección y detalle,
  transiciones DRAFT→OPEN→IN_PROGRESS→FINISHED, borrado con `window.confirm`, generación de
  ronda, standings, pairings y registro de resultados).

---

## 3. Pruebas de integración

### 3.1 El patrón (Java)

Cada microservicio Java tiene **un** test de integración bajo `src/test/java/.../integration/`.
A diferencia de los slices `@WebMvcTest`, este **levanta el contexto Spring completo** y valida
que las peticiones HTTP realmente **persistan datos** en una base real (H2 en memoria).

> **No requieren un comando aparte.** Las clases terminan en `...IntegrationTest`, así que Maven
> Surefire las ejecuta en la fase normal de `mvn test`, **junto con las unitarias** (no son tests
> Failsafe `*IT` que necesiten `mvn verify`). Por eso ya están incluidas en los 530. Para correrlas
> aisladas: `mvn test -Dtest='*IntegrationTest'`. Son **32 en total** (8 + 9 + 5 + 5 + 5).

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PlayerIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired PlayerRepository playerRepository;

    @MockBean EventPublisherService eventPublisherService; // única dependencia externa mockeada
    ...
}
```

Decisiones clave y su porqué:

| Anotación / config | Para qué |
|---|---|
| `@SpringBootTest(RANDOM_PORT)` | Levanta el contexto completo (controllers + services + JPA). |
| `@AutoConfigureMockMvc` | Permite golpear los endpoints con `MockMvc` sin servidor externo. |
| `@ActiveProfiles("test")` | Activa el bloque `test` del `application.yml` (H2, Flyway off). |
| `@DirtiesContext(AFTER_EACH_TEST_METHOD)` | Reinicia el contexto entre tests → aislamiento total. |
| `@MockBean EventPublisherService` | Evita publicar a RabbitMQ (no determinístico); el foco es el resultado observable vía API + BD. |

### 3.2 Configuración del perfil `test`

Vive **dentro de cada `application.yml`** (no hay `application-test.yml` separado), como un bloque
`on-profile: test` (extracto real de `ms-users`):

```yaml
---
# ── Perfil test (H2, Flyway deshabilitado, RabbitMQ mockeado) ──────────────
spring:
  config:
    activate:
      on-profile: test
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL   # H2 emulando Postgres
    driver-class-name: org.h2.Driver
    username: sa
  jpa:
    hibernate:
      ddl-auto: create-drop          # el esquema se crea/destruye en cada arranque
  flyway:
    enabled: false                   # en test NO corren las migraciones; manda JPA
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration
```

Por qué cada cosa:
- **H2 `MODE=PostgreSQL`** — base en memoria que emula el dialecto Postgres, así el SQL generado
  se parece al de producción sin necesitar un Postgres real. (Hay un `WARN` cosmético de
  Hibernate sobre la versión del dialecto; no afecta los tests.)
- **`ddl-auto: create-drop`** — el esquema lo arma JPA desde las entidades, no Flyway.
- **`flyway.enabled: false`** — las migraciones SQL de producción no aplican en H2.
- **`RabbitAutoConfiguration` excluida** + los `RabbitMQConfig` propios anotados `@Profile("!test")**
  — así el contexto carga **sin broker**. Sin esto, Spring intentaría conectar un `ConnectionFactory`
  inexistente y el test fallaría al arrancar.

### 3.3 Qué valida cada test de integración

| Test | Casos | Qué asegura |
|---|---:|---|
| `ms-users / PlayerIntegrationTest` | 8 | GET/PUT de perfil y ELO, resolución por UUID Supabase, conteo real (`totalElements`) — y relee de la BD para confirmar que el cambio **se persistió**. |
| `ms-tournament / TournamentIntegrationTest` | 9 | Alta y consulta de torneos contra H2. |
| `ms-game / GameIntegrationTest` | 5 | Flujo de partida persistido. |
| `ms-notifications / NotificationIntegrationTest` | 5 | Persistencia de notificaciones. |
| `ms-analytics / AnalyticsIntegrationTest` | 5 | Agregaciones persistidas. |

Estilo **Given-When-Then** con `@DisplayName` en español en cada caso. Ejemplo real
(`PlayerIntegrationTest`): tras un `PUT /users/{id}/profile`, no solo verifica el `200` y el JSON
de respuesta, sino que **recarga el Player desde el repositorio** y comprueba que los campos
quedaron efectivamente guardados.

---

## 4. Frontend (Vitest + React Testing Library)

Configuración (`vite.config.ts`): entorno `jsdom`, `setupFiles: ./src/setupTests.ts`, cobertura
con provider **v8**, excluyendo `node_modules/`, `dist/`, `src/main.tsx`, `src/vite-env.d.ts`.

**Patrón de mocks (idéntico en todas las specs):**
- `@tanstack/react-query` → `useQuery`/`useMutation` devuelven stubs controlados (loading, error, data).
- `@chessquery/ui-lib` → passthrough con `data-testid` para poder seleccionar elementos.
- `../api` → `vi.fn()`.

Cada spec cubre las cuatro situaciones típicas de una página: **loading** (skeleton), **error**
(ErrorAlert con el mensaje del backend + fallback "Error desconocido"), **happy-path** (render con
datos) y **navegación** (post-mutate, links).

**Fuera de scope unitario a propósito:** `LiveGame.tsx`. Mockear `chessground` + Supabase Realtime
+ refs de audio es caro y frágil; se cubre por integración manual y testing E2E.

---

## 5. Cobertura

### 5.1 Herramientas y exclusiones

- **Java — JaCoCo.** Reporte en `<módulo>/target/site/jacoco/index.html`. Exclusiones globales
  (no aportan a la métrica): clase `*Application`, `config/**`, `dto/**`, `entity/**`,
  `exception/**`, `migration/**` y configs de storage (`StorageConfig`, `S3Config`).
- **Frontend — v8 (Vitest).** Reporte en `frontend/apps/<app>/coverage/index.html`.

### 5.2 Números reales (última corrida) vs objetivo

| Módulo | Objetivo | Observado | Estado |
|---|---:|---|---|
| `ms-game` | ≥85% líneas | ~90% | ✅ |
| `ms-users` / `ms-tournament` / `ms-notifications` | ≥85% líneas | en rango | ✅ |
| `api-gateway` | ≥80% líneas | en rango | ✅ |
| `ms-analytics` | ≥75% líneas | en rango | ✅ |
| `chess-portal` | — | 76% líneas / 72.5% stmts | ✅ |
| `organizer-panel` | ≥75% líneas | 75.3% (`OrganizerTournaments.tsx` 84%) | ✅ |
| `bff-player` | — | 96% líneas / 71.8% ramas | ✅ |
| `bff-organizer` | — | 94% líneas / 83.6% ramas | ✅ |

> Los umbrales **no bloquean el build aún** (no hay `check` de JaCoCo que falle el `mvn verify`).
> `OrganizerTournaments.tsx` —que antes hundía el promedio a ~35%— ahora está cubierto al **84%**
> de líneas, llevando a `organizer-panel` a **75.3%** global.

### 5.3 Cómo ver un reporte

```bash
xdg-open ~/ChessQuery_FS3/ms-game/target/site/jacoco/index.html
xdg-open ~/ChessQuery_FS3/frontend/apps/chess-portal/coverage/index.html
```

---

## 6. Cómo correr (resumen)

Detalle completo en `TESTING.md`. Lo esencial:

```bash
# Todo de un tirón (requiere JDK 17, Maven, Node, y npm install hecho en los workspaces JS)
bash scripts/test-all.sh

# Un módulo Java con salida detallada (sin -q)
cd ms-game && mvn test -Dtest=LiveGameServiceTest

# Un frontend en watch
cd frontend/apps/chess-portal && npx vitest
```

---

## 7. Pasos futuros ideales

Ordenados por relación impacto/esfuerzo:

1. **Activar umbrales que fallen el build (quality gate).** Hoy los porcentajes son informativos.
   Agregar la regla `jacoco:check` en cada `pom.xml` con el mínimo por módulo (85/80/75%) para que
   un PR que baje la cobertura **rompa el CI**, no solo lo reporte.

2. ~~**Subir `organizer-panel` al nivel del resto.**~~ ✅ **Hecho.** Se ampliaron las specs de
   `OrganizerTournaments.tsx` (listado/KPIs, búsqueda, filtros, estados DRAFT/OPEN/IN_PROGRESS/
   FINISHED, borrado, rondas, standings y pairings): el módulo pasó de ~52% a **75.3%** líneas
   (`OrganizerTournaments.tsx` 35% → 84%).

3. **Reemplazar H2 por Testcontainers (Postgres real) en integración.** H2 en `MODE=PostgreSQL`
   emula, pero no es Postgres. Usar `@Testcontainers` con una imagen de Postgres detectaría
   diferencias de dialecto, migraciones Flyway reales y tipos específicos (JSONB, secuencias).
   Costo: necesita Docker disponible en el runner.

4. **Tests de contrato para los eventos RabbitMQ.** Hoy `EventPublisherService` se mockea en
   integración. Falta una prueba que valide el **esquema** de cada `ChessEvent` (productor) contra
   lo que esperan los consumers — idealmente con un broker real (Testcontainers RabbitMQ) o
   verificación de contrato (p. ej. Spring Cloud Contract).

5. **E2E automatizado.** El flujo F1–F4 de la demo (registro → torneo → partida en vivo → PGN en
   Storage) está documentado pero se corre a mano. Automatizarlo con Playwright contra el stack
   de `docker compose` cerraría la punta de la pirámide.

6. **Cobertura de los BFFs en el reporte.** `bff-player`/`bff-organizer` corren con Jest pero no
   publican métrica de cobertura en CI; agregar `--coverage` y subir el artefacto.

7. **Cubrir `LiveGame.tsx`.** Hoy excluido. Un set mínimo de tests con `chessground` y Supabase
   Realtime stubeados daría red de seguridad al componente más complejo del frontend.

8. **Acelerar la integración.** `@DirtiesContext(AFTER_EACH_TEST_METHOD)` reinicia el contexto en
   cada test (lento). Evaluar `@Transactional` con rollback o `@Sql` de limpieza para no recrear
   el contexto completo, reduciendo el tiempo de la suite.
