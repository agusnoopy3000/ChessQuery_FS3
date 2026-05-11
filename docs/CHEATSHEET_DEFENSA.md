# Cheatsheet — Defensa Oral Parcial N°2

**Asignatura:** DSY1106 Desarrollo Fullstack III — DuocUC
**Equipo:** Martín Mora, Agustín Garrido, Gabriel Espinoza
**Duración total:** 15 min (≈5 min cada integrante)
**Peso defensa:** **70%** de la nota final

---

## 0. Estructura sugerida de la presentación (15 min)

| Minuto | Quién | Tema | Indicador rúbrica |
|---|---|---|---|
| 0:00–0:30 | Agustín | Apertura: contexto del proyecto y stack | — |
| 0:30–5:00 | **Agustín** | **Arquitectura + Arquetipos** | **6 (20%)** |
| 5:00–9:30 | **Martín** | **Patrones de diseño + Pruebas** | **5 (20%) + 8 (15%)** |
| 9:30–13:00 | **Gabriel** | **Branching + Buenas prácticas** | **7 (15%)** |
| 13:00–14:30 | Los 3 | Demo en vivo (escenas A + C) | — |
| 14:30–15:00 | Los 3 | Cierre + preguntas | — |

> **Regla de oro:** cada integrante domina **su** sección. Las preguntas
> del docente las responde quien presentó ese tema (la rúbrica es
> individual).

---

## 1. AGUSTÍN — Líder Senior / Arquitectura

### Tu rol en la defensa

Eres el "**arquitecto del sistema**". Hablas a vista de pájaro: por qué
microservicios, por qué BFF, por qué un arquetipo Maven, qué problemas
de coherencia y escalabilidad resolvieron.

### Lo que TIENES QUE poder decir sin titubear

#### 1.1 ¿Por qué microservicios y no un monolito? (Indicador 6)

> "ChessQuery tiene **bounded contexts** muy distintos: identidad de
> jugadores, gestión de torneos, partidas en vivo y notificaciones.
> Cada uno evoluciona a ritmo distinto y tiene patrones de carga
> diferentes — un torneo se crea una vez al mes, pero una live game
> recibe ~30 jugadas por minuto. Separarlos nos permite escalar
> independientemente, aislar fallos y dejar que cada servicio elija su
> modelo de datos óptimo."

**Archivo de soporte:** `docs/ANALISIS_PATRONES.md` §3.1
**Métrica concreta:** 7 microservicios + 7 bases de datos PostgreSQL
(database-per-service).

#### 1.2 ¿Qué es el BFF y por qué 3? (Indicador 6)

> "Cada portal — Player, Organizer, Admin — tiene necesidades distintas
> de composición de datos. Si el frontend hablara directo con los
> microservicios, generaría over-fetching y duplicaría lógica de
> agregación. El BFF compone respuestas optimizadas para cada audiencia.
> Por ejemplo, `bff-player` combina perfil + torneos activos + última
> partida en una sola llamada `GET /player/dashboard`."

**Archivos:** `bff-player/`, `bff-organizer/`, `bff-admin/` (NestJS 10).

#### 1.3 ¿Qué hace el API Gateway? (Indicador 6)

> "Es el único punto de entrada externo. Hace tres cosas: enrutamiento
> por path, validación de JWT contra los JWKS de Supabase, y rate
> limiting con Redis (100 req/min por IP). Los microservicios confían
> en headers `X-User-Id`, `X-User-Role` propagados por el gateway —
> ellos no saben de JWT."

**Archivo:** `api-gateway/src/main/java/cl/chessquery/gateway/`
- `filter/SupabaseJwtAuthFilter.java` (valida JWT)
- `auth/PlayerIdResolver.java` (UUID Supabase → player.id con caché Caffeine)

#### 1.4 ¿Qué es el Arquetipo Maven y para qué sirve? (Indicador 6)

> "Cuando tienes 7 microservicios con el mismo stack — Spring Boot
> 3.2.4, Flyway, RabbitMQ, Resilience4j, Springdoc, Lombok, JaCoCo —
> sin un arquetipo cada uno reimplementa el `pom.xml` y deriva. Creamos
> `chessquery-ms-archetype` que cristaliza esas decisiones: versiones
> de dependencias, Dockerfile multi-stage, application.yml plantilla
> con datasource + rabbit, Flyway init.sql. Generar un MS nuevo es un
> `mvn archetype:generate` con 6 parámetros."

**Archivo:** `archetypes/chessquery-ms-archetype/README.md`
**Comando de ejemplo (saberlo de memoria):**
```bash
mvn archetype:generate \
  -DarchetypeGroupId=cl.chessquery.archetypes \
  -DarchetypeArtifactId=chessquery-ms-archetype \
  -DserviceName=rating -DservicePort=8087
```

#### 1.5 Database-per-service y referencias cross-service (Indicador 6)

> "Cada MS tiene su BD propia (`user_db`, `tournament_db`, `game_db`,
> etc.). Las referencias cross-service como `player_id` en
> `tournament_db` son BIGINT planos **sin foreign key** — la integridad
> referencial se mantiene a nivel aplicación. Esto sacrifica garantías
> SQL pero da independencia total para escalar y desplegar cada MS
> sin tocar a los otros."

#### 1.6 Event-Driven Architecture (Indicador 6)

> "Usamos RabbitMQ como bus de eventos con un topic exchange
> `ChessEvents`. Cuando termina una partida, `ms-game` publica
> `game.finished` y `elo.updated` (uno por jugador). Los servicios
> interesados — `ms-users` para actualizar ELO, `ms-analytics` para
> stats, `ms-notifications` para el push in-app — se suscriben a las
> routing keys que les importan, cada uno en su cola dedicada.
> Acoplamiento débil, escalabilidad horizontal por consumer."

**Routing keys del proyecto:** `user.*`, `tournament.*`, `game.*`,
`elo.*`, `rating.*`, `etl.*`.

### Preguntas trampa que te van a hacer

**P:** "Si los MS no tienen FK entre sí, ¿qué pasa si borras un Player que tiene partidas?"
**R:** "No permitimos hard delete de Player. La consistencia es eventual: si llegara a borrarse, las partidas quedarían con un `player_id` huérfano y el frontend muestra `Jugador eliminado` sin romperse. En sistemas distribuidos esto es preferible a un 2PC entre BDs."

**P:** "¿Por qué Supabase y no `ms-auth`?"
**R:** "`ms-auth` lo implementamos en la Parcial N°1 — JWT HS256 + refresh tokens SHA-256 + BCrypt. Para esta fase migramos a Supabase para acelerar features de auth (magic link, OAuth, recuperación de password) sin invertir tiempo en infra. Mantuvimos `ms-auth` en el repo como referencia y para escenarios on-premise. El gateway ahora valida contra JWKS de Supabase en lugar de `ms-auth/auth/validate`."

**P:** "¿Por qué no Kubernetes?"
**R:** "Para 7 servicios + 7 BDs + 1 broker en un entorno académico, Docker Compose da el mismo isolation con orden de magnitud menos complejidad operacional. K8s sería sobreingeniería. El sistema está diseñado para portarse a K8s sin cambios de código (12-factor)."

---

## 2. MARTÍN — Developer / Patrones de Diseño + Pruebas

### Tu rol en la defensa

Eres el "**artesano del código**". Hablas de patrones concretos
implementados, archivos, líneas, justificación técnica. Y muestras
evidencia de pruebas unitarias.

### Lo que TIENES QUE poder decir sin titubear

#### 2.1 Factory Method — `PairingStrategyFactory` (Indicador 5)

> "En `ms-tournament` tenemos 3 formatos de pairing: Suizo, Round-Robin,
> Knockout. Sin Factory, cada llamada tendría un `switch` y conocería
> las 3 implementaciones, violando Open/Closed. Implementamos
> `PairingStrategyFactory.getStrategy(format)` que devuelve la strategy
> correspondiente. Agregar un formato nuevo — por ejemplo Sistema
> Acelerado — es una clase nueva y un case nuevo, sin modificar
> callers."

**Archivo:** `ms-tournament/src/main/java/cl/chessquery/tournament/pairing/PairingStrategyFactory.java`

#### 2.2 Strategy — `PairingStrategy` y sus 3 implementaciones (Indicador 5)

> "El Factory devuelve una `PairingStrategy` (interfaz). Tres
> implementaciones: `SwissPairingStrategy` ordena por puntaje y empareja
> con Buchholz como tie-breaker, `RoundRobinPairingStrategy` genera
> todas las combinaciones, `KnockoutPairingStrategy` arma el árbol de
> eliminación. Cada una se testea aislada — `SwissPairingStrategyTest`
> cubre la lógica del Suizo sin tocar las otras."

**Archivos:**
- `pairing/PairingStrategy.java` (interfaz)
- `pairing/SwissPairingStrategy.java`
- `pairing/RoundRobinPairingStrategy.java`
- `pairing/KnockoutPairingStrategy.java`
- `src/test/.../SwissPairingStrategyTest.java`

#### 2.3 Circuit Breaker — Resilience4j (Indicador 5)

> "`ms-tournament` consulta a `ms-users` para obtener el ELO real de un
> jugador al hacer seeding del torneo. Si `ms-users` se cae,
> normalmente todas las inscripciones fallarían en cascada. Aplicamos
> Circuit Breaker con Resilience4j: tras N fallos consecutivos, el
> circuito se abre y devuelve un fallback de ELO 1500 (neutro FIDE)
> sin esperar timeouts. El sistema mantiene disponibilidad parcial y
> se recupera automáticamente cuando `ms-users` vuelve."

**Anotación clave:**
```java
@CircuitBreaker(name = "usersService", fallbackMethod = "fallbackElo")
public Integer getPlayerElo(Long playerId) { ... }
private Integer fallbackElo(Long id, Throwable t) { return 1500; }
```

#### 2.4 Observer / Publish-Subscribe — RabbitMQ (Indicador 5)

> "Cuando termina una partida, hay 4 efectos: ELO en `ms-users`,
> stats en `ms-analytics`, push in-app en `ms-notifications`,
> actualizar pairing en `ms-tournament`. Hacerlo síncrono con REST
> generaría acoplamiento fuerte y SPOF. Aplicamos Pub-Sub: `ms-game`
> publica un evento al exchange `ChessEvents` con routing key
> `game.finished` y cada consumidor — en su cola dedicada — reacciona
> a lo que le importa. Agregar un suscriptor nuevo no requiere tocar
> `ms-game`."

**Archivos:**
- Productor: `ms-game/.../service/EventPublisherService.java`
- Consumidores: `ms-notifications/.../messaging/GameEventsConsumer.java`,
  `ms-users/.../messaging/EloUpdatedConsumer.java`

#### 2.5 Idempotent Receiver (Indicador 5, plus moderno)

> "Bajo concurrencia, el frontend dispara N requests paralelas a
> `POST /users/provision` apenas hay sesión Supabase. Sin protección,
> el primero crea el Player y los otros chocan con unique constraint →
> HTTP 500. Implementamos un Idempotent Receiver: catch
> `DataIntegrityViolationException`, limpieza del EntityManager,
> re-read y devolución del ganador. Verificado experimentalmente con
> 5 requests concurrentes: 5×HTTP 200, mismo `id`. Antes: 1×200 + 4×500.
> En `ms-notifications` el mismo patrón se aplica a nivel de mensaje
> usando una tabla `processed_event` para deduplicación."

**Archivo:** `ms-users/src/main/java/cl/chessquery/users/service/PlayerService.java`

#### 2.6 Pruebas unitarias y cobertura (Indicador 8)

**Suite actual:**

| Test | Cubre | Microservicio |
|---|---|---|
| `EloCalculatorTest` | Fórmula FIDE, ajuste de K | ms-game |
| `LiveGameServiceTest` | Lógica de sesión, validación de jugadas | ms-game |
| `SupabaseStorageServiceTest` | Upload + presigned URL | ms-game |
| `SwissPairingStrategyTest` | Algoritmo Suizo + Buchholz | ms-tournament |
| `AgeCategoryTest` | Categorización SUB8-SUB20, OPEN, SENIOR | ms-users |
| `PlayerSearchServiceTest` | Fuzzy search pg_trgm | ms-users |
| `UserControllerIntegrationTest` | E2E con H2 in-memory | ms-users |
| `UserRegisteredConsumerTest` | Idempotencia del consumer | ms-users |

**Para mostrar cobertura en la defensa:**

```bash
cd ms-tournament && mvn test jacoco:report
open target/site/jacoco/index.html
```

Si te preguntan cobertura específica: **objetivo 60% por módulo
(rúbrica del curso)**. Lo abres en el navegador en vivo si pueden.

### Preguntas trampa que te van a hacer

**P:** "¿Por qué no usaron `Optional<PairingStrategy>` en lugar del Factory?"
**R:** "Devolver `Optional` obliga al caller a manejar el caso `null`, pero ya tenemos un enum cerrado `TournamentFormat` con 3 valores válidos. Devolver la strategy directamente es más expresivo. Si el enum se ampliara dinámicamente (plugin model), Optional sería más correcto."

**P:** "¿Qué nivel de cobertura tienen?"
**R:** Tener el número exacto. Si JaCoCo no está corrido, decir "objetivo del proyecto: 60%, cubriendo lógica de negocio crítica — ELO, pairings, idempotencia. Los controllers se cubren con tests de integración con H2."

**P:** "¿Probaron escenarios de fallo en RabbitMQ?"
**R:** "El consumer de notificaciones tiene re-delivery automático configurado (3 reintentos con backoff exponencial) + idempotencia vía `processed_event`. Si un consumer falla a mitad de procesamiento, el mensaje vuelve a la cola y se reprocesa sin duplicar el efecto."

---

## 3. GABRIEL — Branching + Buenas Prácticas

### Tu rol en la defensa

Eres el "**dueño del proceso**". Hablas de cómo el equipo coordinó
trabajo, por qué la estrategia de branching, cómo se resolvieron
conflictos. **Indicador 7 (15%)** te toca completo.

### Lo que TIENES QUE poder decir sin titubear

#### 3.1 Estrategia de branching (Indicador 7)

> "Adoptamos una variante de **Trunk-Based Development** con feature
> branches cortas, basada en GitFlow simplificado. La rama `main` está
> protegida — sólo se actualiza vía Pull Request aprobado. Cada feature
> tiene su rama con prefijo: `feat/ms-<servicio>` para microservicios
> nuevos, `feat/<app>` para frontends, `fix/<scope>` para correcciones,
> `docs/<tema>` para documentación."

**Archivo:** `docs/PLAN_BRANCHING.md`

#### 3.2 Convenciones de commits

> "**Conventional Commits** en todo el proyecto. Cada commit empieza con
> el tipo: `feat:`, `fix:`, `docs:`, `refactor:`. Esto nos permite
> generar changelogs automáticos y entender la naturaleza de cada
> cambio sin abrir el diff. Decidimos **no usar `Co-Authored-By`**
> porque los avatares tapaban el grafo de GitHub."

#### 3.3 PRs mergeados (saber al menos 5 de memoria)

| PR | Rama | Contenido |
|---|---|---|
| #5 | `feat/ms-auth` | JWT, BCrypt, refresh tokens |
| #6 | `feat/ms-users` | Player + fuzzy search |
| #7 | `feat/ms-tournament` | Factory + Strategy pairings |
| #8 | `feat/ms-game` | ELO + PGN + openings |
| #13 | `feat/organizer-panel` | Panel organizador |
| #14 | `feat/admin-panel` | Panel admin |
| #16 | `feat/player-registration-flow` | Flujo de registro |

**Total commits en `main`: 133.**

#### 3.4 Ejemplo de gestión de conflictos (Indicador 7 — pregunta segura)

> "Caso real: al mergear `feat/admin-panel` (PR #14) después de
> `feat/organizer-panel` (PR #13), ambas ramas habían modificado el
> archivo `frontend/apps/*/api.ts` agregando endpoints distintos. Git
> marcó conflicto en los imports. Lo resolvimos con un rebase de
> `feat/admin-panel` sobre el `main` actualizado, unificando manualmente
> los imports y verificando que ambos endpoints quedaran. Commit de
> resolución: `67a806b — merge: resolver conflictos con main tras
> #13/#14`."

> "Caso 2: `feat/ajefech-integration` modificó `Player.java` agregando
> el campo `enrichmentSource` justo cuando `feat/chess-portal-pages`
> cambiaba el DTO. El conflicto fue en el mapper. Lo resolvimos
> aceptando ambos cambios — los campos no eran excluyentes — y
> regenerando los tests del mapper. Commit: `f2d9ef5`."

#### 3.5 Beneficios de la estrategia (cierre)

> "Tres beneficios concretos observados:
> 1. **Paralelismo real**: los tres trabajamos al mismo tiempo en
>    ramas distintas sin pisarnos.
> 2. **Reversibilidad**: si un PR introducía un bug, lo revertíamos
>    sin afectar el trabajo en curso de los demás.
> 3. **Code review forzado**: ningún cambio entra a `main` sin
>    revisión — atrapamos varios bugs en review antes de mergear."

### Preguntas trampa que te van a hacer

**P:** "¿Por qué no usaron `develop` como rama de integración?"
**R:** "Para un equipo de 3 personas con releases continuas, una rama `develop` agrega ceremonia sin valor. Llegaríamos a `main` igual con un merge extra. Trunk-Based con PRs cortos a `main` da el mismo control con menos pasos. Si el equipo creciera a >10 personas o tuviéramos releases programados, sí consideraríamos `develop`."

**P:** "¿Cómo aseguran que no se rompa `main`?"
**R:** "Tres mecanismos: (1) PRs requieren al menos un review, (2) los commits siguen Conventional Commits para ser auto-documentables, (3) localmente los tests pasan antes de pedir review. En la siguiente iteración (post-Parcial N°2) habilitaremos GitHub Actions con CI obligatorio."

**P:** "¿Qué pasa si dos PRs tocan el mismo archivo?"
**R:** "Lo manejamos con rebase + merge manual del conflicto, no con merge commits sucios. El PR más nuevo hace `git rebase main`, resuelve el conflicto localmente y re-pusha. Esto mantiene el historial lineal."

---

## 4. Demo en vivo (los 3 juntos, 1:30 min)

**Quien maneja el portátil:** Agustín.

**Escena rápida (90 segundos):**

1. Login con `bruno@demo.cl` (5s)
2. Crear live game → invitar a `ana@demo.cl` por email (15s)
3. **Mostrar el toast in-app de invitación en la ventana de Ana** (cierre
   estilo) (10s)
4. Jugar 3 jugadas (e2e4, e7e5, g1f3) — material capturado visible (20s)
5. Rendirse → modal de fin + PGN guardado (10s)
6. Switch a `carla@demo.cl` en organizer-panel → mostrar el torneo
   creado en el dry-run con su standings (Buchholz/Sonneborn) (30s)

**Si algo falla en vivo, plan B:**
- Saltar al video grabado de respaldo
- O cambiar a otro flujo (registro de un usuario nuevo desde 0)

---

## 5. Comodín — datos numéricos a tener de memoria

Si te preguntan números específicos:

| Dato | Valor |
|---|---|
| Microservicios Java | 7 |
| BFFs NestJS | 3 |
| Microservicios Python | 1 (ms-etl) |
| Bases de datos PostgreSQL | 7 |
| Patrones de diseño aplicados | 8 |
| Patrones arquitectónicos | 6 |
| Commits en `main` al cierre | 133 |
| PRs mergeados | 16 |
| Total LOC backend Java | ~14k |
| Total LOC frontend | ~6k |
| Aperturas ECO en seed | 90+ |
| Memoria total stack demo M1 | ~2 GB idle, ~3 GB activa |

---

## 6. Cosas que NO decir (sí decir lo contrario)

| ❌ Evita decir | ✅ Mejor di |
|---|---|
| "Lo hicimos así porque estaba en el tutorial" | "Lo elegimos porque resuelve X problema concreto" |
| "No nos dio tiempo de hacer tests" | "Priorizamos cobertura en la lógica crítica: ELO, pairings, idempotencia" |
| "No sabemos cuánta cobertura tenemos" | "Objetivo del proyecto: 60%. JaCoCo configurado, puedo mostrar el reporte" |
| "Usamos monorepo porque era más fácil" | "Monorepo nos permite commits atómicos cross-componente y workspaces NPM coherentes" |
| "ms-auth no se usa" | "ms-auth lo migramos a Supabase para acelerar features de auth; mantenemos el módulo como referencia y opción on-premise" |

---

## 7. Checklist 30 min antes de la defensa

- [ ] Cada integrante leyó su sección de este cheatsheet
- [ ] Estructura ChessQuery_FS3 abierta en VS Code con archivos clave
      en pestañas (Agustín: api-gateway, archetype README;
      Martín: PairingStrategyFactory + PlayerService; Gabriel:
      git log + PLAN_BRANCHING.md)
- [ ] Demo levantada y warm-up hecho (1 live game completo de práctica)
- [ ] Pestaña con `mvn jacoco:report` corrido y `index.html` abierto
- [ ] Pestaña con `git log --oneline --merges | head` lista
- [ ] Pestaña con Supabase Studio (apagado por default; encender solo
      si lo van a mostrar)
- [ ] Plan B (video grabado) accesible en escritorio
