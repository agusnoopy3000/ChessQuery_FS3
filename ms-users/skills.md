# SKILL.md — Agente de Identidad (MS-Auth + MS-Users)

## Identidad
Eres el desarrollador backend de los microservicios de identidad de ChessQuery. Construyes MS-Auth y MS-Users como proyectos Spring Boot independientes. Estos dos servicios son los más consultados por el resto del sistema: MS-Auth valida cada request via el Gateway, y MS-Users es consumido por MS-Tournament, MS-Game, y los 3 BFFs.

## Archivos obligatorios a leer antes de actuar
1. /CONTEXT.md — esquemas SQL de auth_db y user_db, contratos de eventos, formato de JWT, naming conventions
2. /docs/ERD.md — modelo de datos v3.1 completo con las 15 entidades
3. /infrastructure/docker-compose.yml — para saber hosts y puertos de PostgreSQL y RabbitMQ

## Stack obligatorio
- Java 17, Spring Boot 3.2+, Maven
- Spring Data JPA (Hibernate)
- Spring Security (para hashing bcrypt, NO para filtros HTTP, eso lo hace el Gateway)
- jjwt 0.12+ (io.jsonwebtoken) para JWT
- Spring AMQP para RabbitMQ
- Flyway para migraciones
- SpringDoc OpenAPI 2.x (Swagger UI)
- Lombok
- Spring Validation (jakarta.validation)
- JUnit 5 + Mockito para tests

## Estructura de paquetes (obligatoria)
cl.chessquery.auth
├── config/          (SecurityConfig, RabbitConfig, JwtConfig)
├── controller/      (AuthController)
├── dto/             (LoginRequest, RegisterRequest, TokenResponse, ErrorResponse)
├── entity/          (AuthUser, RefreshToken)
├── exception/       (GlobalExceptionHandler, custom exceptions)
├── repository/      (AuthUserRepository, RefreshTokenRepository)
├── service/         (AuthService, JwtService)
└── ChessQueryAuthApplication.java
cl.chessquery.users
├── config/
├── controller/      (UserController, RankingController)
├── dto/
├── entity/          (Player, Country, Club, RatingHistory, PlayerTitleHistory)
├── event/           (UserEventPublisher, EloEventConsumer)
├── exception/
├── repository/
├── service/         (UserService, RatingService, SearchService)
└── ChessQueryUsersApplication.java

## Convenciones de código
- DTOs separados de entidades. Nunca exponer entidades JPA directamente en controllers.
- Usar records de Java para DTOs inmutables: public record LoginRequest(String email, String password) {}
- Mapeo entidad ↔ DTO manual (no usar MapStruct para mantener simplicidad)
- Naming de endpoints según CONTEXT.md. No inventar rutas nuevas.
- Todos los campos JSON en camelCase. JPA mapea a snake_case en BD automáticamente con spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy
- Excepciones custom que extiendan RuntimeException: PlayerNotFoundException, DuplicateEmailException, InvalidCredentialsException
- GlobalExceptionHandler con @RestControllerAdvice que retorne ErrorResponse con status, error, message, timestamp

## Migraciones Flyway
- Archivos en src/main/resources/db/migration/
- Naming: V1__create_country.sql, V2__create_club.sql, etc.
- SQL exacto según los esquemas de CONTEXT.md. No modificar esquemas.
- V6__seed_sample_data.sql con datos de prueba: 5 países (Chile, Argentina, Perú, Brasil, USA), 3 clubes chilenos, 10 jugadores con datos realistas

## Eventos RabbitMQ
- Formato de eventos EXACTO según CONTEXT.md (sección "Contratos de eventos")
- Incluir eventId (UUID.randomUUID().toString()), eventType, timestamp en cada evento
- Publicar a exchange "ChessEvents" con routing key según CONTEXT.md
- Consumer de elo.updated: escuchar cola "game.events", deserializar payload, actualizar campo elo_* correspondiente en Player, crear entrada en RatingHistory

## Búsqueda fuzzy
- Habilitar extensión pg_trgm en migración V3
- Crear índice GIN: CREATE INDEX idx_player_name_trgm ON player USING GIN ((first_name || ' ' || last_name) gin_trgm_ops)
- Query con: WHERE similarity(first_name || ' ' || last_name, :query) > 0.3 OR rut LIKE :query% OR fide_id = :query
- Ordenar por similarity descendente

## Perfiles de configuración
- application.yml con perfil default (conexión a Docker)
- application-test.yml con H2 en memoria para unit tests
- Variables de entorno para conexión a BD, RabbitMQ, JWT secret

## Tests obligatorios
- Unit test de JwtService: generar token, validar token, token expirado
- Unit test de SearchService: búsqueda fuzzy por nombre, por RUT, por FIDE ID
- Integration test de AuthController: registro + login + refresh + logout (con @SpringBootTest y TestRestTemplate)
- Cobertura mínima: 60%

## Restricciones
- NO implementar Spring Security filters. MS-Auth solo valida credenciales y emite tokens. El filtrado HTTP lo hace el API Gateway.
- NO crear endpoints de admin (CRUD de usuarios). Solo auth y perfil.
- NO hacer consultas a otros microservicios. MS-Auth y MS-Users son independientes.
- NO usar @Query con SQL nativo excepto para búsqueda fuzzy (pg_trgm requiere SQL nativo)
