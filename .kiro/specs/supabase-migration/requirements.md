# Requirements Document: Supabase Migration

## Introduction

ChessQuery es una plataforma de microservicios para ajedrez competitivo que actualmente opera con 9 servicios backend, MinIO para object storage, y PostgreSQL para múltiples bases de datos. Esta migración parcial tiene como objetivo simplificar la arquitectura reemplazando MS-Auth y MinIO con Supabase, mientras se mantienen los microservicios custom que implementan lógica de negocio compleja específica del dominio de ajedrez competitivo.

La migración reducirá la cantidad de componentes de 11 a 8 (-27%), simplificará la gestión de autenticación y almacenamiento de archivos, y permitirá al equipo enfocarse en la lógica de negocio única de ChessQuery (algoritmos de pareo de torneos, cálculo ELO FIDE, sincronización con federaciones externas).

**Alcance de la migración:**
- **Reemplazar:** MS-Auth (Spring Boot), MinIO, PostgreSQL auth_db
- **Con:** Supabase Auth, Supabase Storage, Supabase Auth DB (gestionado)
- **Mantener:** MS-Users, MS-Tournament, MS-Game, MS-ETL, MS-Notifications, BFF-Player, BFF-Organizer, API Gateway

**Timeline:** 2 semanas para implementación y demo funcional.

---

## Glossary

- **Supabase**: Plataforma Backend-as-a-Service que proporciona autenticación, base de datos PostgreSQL, storage y APIs REST/Realtime
- **MS-Auth**: Microservicio actual de autenticación en Spring Boot que será reemplazado
- **MinIO**: Servicio actual de object storage compatible con S3 que será reemplazado
- **API_Gateway**: Spring Cloud Gateway que enruta requests y valida JWT
- **Supabase_Auth**: Servicio de autenticación de Supabase que gestiona JWT, refresh tokens y sesiones
- **Supabase_Storage**: Servicio de almacenamiento de objetos de Supabase con CDN integrado
- **Supabase_Client**: Librería JavaScript/TypeScript (@supabase/supabase-js) para interactuar con Supabase desde frontends
- **JWT**: JSON Web Token usado para autenticación stateless
- **Refresh_Token**: Token de larga duración (7 días) usado para obtener nuevos access tokens
- **Access_Token**: JWT de corta duración (1 hora) usado para autenticar requests
- **Chess_Portal**: Frontend para jugadores (workspace de jugador)
- **Organizer_Panel**: Frontend para organizadores de torneos
- **PGN_File**: Portable Game Notation, formato estándar de texto para representar partidas de ajedrez
- **Presigned_URL**: URL temporal firmada que permite acceso directo a un archivo en storage sin autenticación adicional
- **RLS**: Row Level Security, sistema de políticas de seguridad a nivel de fila en PostgreSQL/Supabase
- **User_Role**: Rol de usuario en el sistema (PLAYER, ORGANIZER, ADMIN)
- **Custom_Microservices**: Microservicios que implementan lógica de negocio específica de ChessQuery
- **Supabase_Local**: Instancia de Supabase ejecutándose localmente vía Docker para desarrollo
- **Service_Key**: API key de Supabase con privilegios administrativos para operaciones backend
- **Anon_Key**: API key pública de Supabase para operaciones desde frontend
- **X-User-Id**: Header HTTP que contiene el ID del usuario autenticado
- **X-User-Email**: Header HTTP que contiene el email del usuario autenticado
- **X-User-Role**: Header HTTP que contiene el rol del usuario autenticado

---

## Requirements

### Requirement 1: Supabase Local Development Environment

**User Story:** Como desarrollador, quiero ejecutar Supabase localmente en Docker, para que pueda desarrollar y probar la migración sin depender de servicios cloud externos.

#### Acceptance Criteria

1. WHEN Supabase CLI es ejecutado con el comando `supabase start`, THE Supabase_Local SHALL inicializar todos los servicios necesarios (Auth, Storage, PostgreSQL, Studio UI) en contenedores Docker
2. THE Supabase_Local SHALL exponer la API en http://localhost:54321 con endpoints funcionales para Auth y Storage
3. THE Supabase_Local SHALL exponer Studio UI en http://localhost:54323 para gestión visual de la base de datos
4. THE Supabase_Local SHALL generar y proporcionar JWT secret, anon key y service key válidos al inicializar
5. WHEN un desarrollador ejecuta `supabase stop`, THE Supabase_Local SHALL detener todos los contenedores y preservar el estado de la base de datos
6. THE Supabase_Local SHALL integrarse con el docker-compose.yml existente de ChessQuery sin conflictos de puertos

---

### Requirement 2: User Authentication with Supabase Auth

**User Story:** Como usuario del sistema (jugador, organizador o administrador), quiero autenticarme usando Supabase Auth, para que pueda acceder a las funcionalidades de la plataforma de forma segura.

#### Acceptance Criteria

1. WHEN un usuario proporciona email y password válidos, THE Supabase_Auth SHALL autenticar al usuario y retornar un access token JWT válido por 1 hora
2. WHEN un usuario proporciona email y password válidos, THE Supabase_Auth SHALL generar y retornar un refresh token válido por 7 días
3. THE Supabase_Auth SHALL incluir en el JWT los claims: sub (user ID), email, y role (PLAYER, ORGANIZER o ADMIN) en user_metadata
4. WHEN un usuario se registra, THE Supabase_Auth SHALL crear un registro en auth.users y un perfil en public.user_profiles con el rol especificado
5. WHEN un access token expira, THE Supabase_Auth SHALL aceptar un refresh token válido y emitir un nuevo access token
6. WHEN un usuario cierra sesión, THE Supabase_Auth SHALL revocar la sesión actual y marcar el refresh token como inválido
7. IF un usuario intenta autenticarse con credenciales inválidas, THEN THE Supabase_Auth SHALL retornar un error 401 con mensaje descriptivo

---

### Requirement 3: JWT Validation in API Gateway

**User Story:** Como API Gateway, quiero validar JWT tokens emitidos por Supabase, para que pueda autorizar requests y propagar información del usuario a los microservicios downstream.

#### Acceptance Criteria

1. WHEN un request llega con header Authorization Bearer token, THE API_Gateway SHALL validar la firma del JWT usando el JWT secret de Supabase
2. WHEN un JWT es válido, THE API_Gateway SHALL extraer los claims sub, email y role del token
3. WHEN un JWT es válido, THE API_Gateway SHALL agregar headers X-User-Id, X-User-Email y X-User-Role al request downstream
4. IF un JWT es inválido o ha expirado, THEN THE API_Gateway SHALL retornar 401 Unauthorized sin llamar a servicios downstream
5. IF un request no incluye header Authorization, THEN THE API_Gateway SHALL retornar 401 Unauthorized para rutas protegidas
6. THE API_Gateway SHALL validar JWT localmente sin llamar a servicios externos para cada request (validación stateless)

---

### Requirement 4: Frontend Authentication Integration

**User Story:** Como desarrollador frontend, quiero integrar Supabase Client en Chess Portal y Organizer Panel, para que los usuarios puedan autenticarse desde las aplicaciones web.

#### Acceptance Criteria

1. THE Chess_Portal SHALL inicializar Supabase_Client con SUPABASE_URL y SUPABASE_ANON_KEY al cargar la aplicación
2. THE Organizer_Panel SHALL inicializar Supabase_Client con SUPABASE_URL y SUPABASE_ANON_KEY al cargar la aplicación
3. WHEN un usuario completa el formulario de registro, THE Chess_Portal SHALL llamar a supabase.auth.signUp() con email, password y role en metadata
4. WHEN un usuario completa el formulario de login, THE Chess_Portal SHALL llamar a supabase.auth.signInWithPassword() con email y password
5. WHEN Supabase_Auth retorna tokens exitosamente, THE Chess_Portal SHALL almacenar el access token en memoria y el refresh token en localStorage
6. THE Supabase_Client SHALL manejar automáticamente el refresh de access tokens cuando expiran
7. WHEN un usuario cierra sesión, THE Chess_Portal SHALL llamar a supabase.auth.signOut() y limpiar tokens del storage
8. THE Chess_Portal SHALL suscribirse a cambios de estado de autenticación usando supabase.auth.onAuthStateChange() para actualizar la UI

---

### Requirement 5: Role-Based Access Control

**User Story:** Como administrador del sistema, quiero que los roles de usuario (PLAYER, ORGANIZER, ADMIN) se gestionen en Supabase, para que el control de acceso sea consistente en toda la plataforma.

#### Acceptance Criteria

1. WHEN un usuario se registra, THE Supabase_Auth SHALL almacenar el role en la tabla public.user_profiles con constraint CHECK (role IN ('PLAYER', 'ORGANIZER', 'ADMIN'))
2. THE Supabase_Auth SHALL incluir el role en el JWT como parte de user_metadata
3. THE API_Gateway SHALL propagar el role a microservicios downstream mediante el header X-User-Role
4. THE Custom_Microservices SHALL confiar en el header X-User-Role sin validación adicional (validación realizada por API Gateway)
5. WHEN un administrador actualiza el role de un usuario, THE Supabase_Auth SHALL reflejar el cambio en el próximo JWT emitido
6. THE Supabase_Auth SHALL aplicar Row Level Security (RLS) policies para que usuarios solo puedan leer su propio perfil

---

### Requirement 6: PGN File Storage Migration

**User Story:** Como MS-Game, quiero almacenar archivos PGN en Supabase Storage en lugar de MinIO, para que la gestión de archivos esté centralizada en Supabase.

#### Acceptance Criteria

1. THE Supabase_Storage SHALL proporcionar un bucket llamado "chessquery-pgn" para almacenar archivos PGN
2. WHEN MS-Game guarda una partida, THE MS-Game SHALL subir el archivo PGN a Supabase_Storage usando la ruta games/{year}/{month}/{gameId}.pgn
3. THE MS-Game SHALL usar el Service_Key de Supabase para autenticar operaciones de subida de archivos
4. THE MS-Game SHALL almacenar la key del archivo (games/2026/04/4521.pgn) en el campo GAME.pgn_storage_key de la base de datos
5. THE MS-Game SHALL configurar el Content-Type como "application/x-chess-pgn" al subir archivos
6. THE Supabase_Storage SHALL aplicar RLS policies para permitir que usuarios autenticados suban archivos y cualquier usuario pueda leer archivos
7. THE Supabase_Storage SHALL rechazar archivos PGN mayores a 1MB

---

### Requirement 7: Presigned URL Generation

**User Story:** Como usuario, quiero descargar archivos PGN de partidas, para que pueda analizar las partidas offline usando software de ajedrez.

#### Acceptance Criteria

1. WHEN un usuario solicita descargar un PGN, THE MS-Game SHALL generar una presigned URL usando Supabase Storage API
2. THE MS-Game SHALL configurar la presigned URL con expiración de 1 hora (3600 segundos)
3. THE Presigned_URL SHALL permitir acceso directo al archivo sin requerir autenticación adicional
4. WHEN la presigned URL expira, THE Supabase_Storage SHALL retornar 403 Forbidden al intentar acceder al archivo
5. THE MS-Game SHALL retornar la presigned URL completa al cliente en formato: {SUPABASE_URL}/storage/v1/object/sign/chessquery-pgn/{key}?token={signature}

---

### Requirement 8: Database Schema for User Profiles

**User Story:** Como Supabase, quiero gestionar perfiles de usuario en una tabla custom, para que pueda almacenar información adicional más allá de auth.users.

#### Acceptance Criteria

1. THE Supabase_Local SHALL crear una tabla public.user_profiles con columnas: id (UUID FK a auth.users), role (TEXT), created_at (TIMESTAMPTZ)
2. THE user_profiles.role SHALL tener constraint CHECK (role IN ('PLAYER', 'ORGANIZER', 'ADMIN'))
3. THE user_profiles.id SHALL ser PRIMARY KEY y FOREIGN KEY a auth.users(id) con ON DELETE CASCADE
4. WHEN un usuario se registra, THE Supabase_Auth SHALL insertar automáticamente un registro en user_profiles mediante trigger o RPC
5. THE user_profiles SHALL aplicar RLS policy para que usuarios solo puedan SELECT su propio perfil
6. THE user_profiles SHALL permitir que service_role key pueda INSERT, UPDATE y DELETE sin restricciones RLS

---

### Requirement 9: Environment Configuration

**User Story:** Como desarrollador, quiero configurar variables de entorno para Supabase en todos los servicios, para que puedan conectarse correctamente a Supabase Local y Cloud.

#### Acceptance Criteria

1. THE API_Gateway SHALL leer las variables SUPABASE_URL y SUPABASE_JWT_SECRET del entorno
2. THE MS-Game SHALL leer las variables SUPABASE_URL y SUPABASE_SERVICE_KEY del entorno
3. THE Chess_Portal SHALL leer las variables VITE_SUPABASE_URL y VITE_SUPABASE_ANON_KEY del entorno
4. THE Organizer_Panel SHALL leer las variables VITE_SUPABASE_URL y VITE_SUPABASE_ANON_KEY del entorno
5. THE docker-compose.yml SHALL definir variables de entorno para Supabase Local con valores por defecto seguros
6. THE .env.example SHALL documentar todas las variables de Supabase requeridas con descripciones claras
7. THE .env SHALL estar en .gitignore para prevenir exposición de secrets

---

### Requirement 10: Backward Compatibility During Migration

**User Story:** Como equipo de desarrollo, quiero mantener compatibilidad con contratos existentes durante la migración, para que los microservicios custom no requieran cambios en su lógica de negocio.

#### Acceptance Criteria

1. THE API_Gateway SHALL continuar propagando headers X-User-Id, X-User-Email y X-User-Role con el mismo formato que MS-Auth
2. THE Custom_Microservices SHALL continuar confiando en headers downstream sin cambios en su código de autorización
3. THE MS-Users SHALL mantener su base de datos user_db y modelo de datos PLAYER sin cambios
4. THE MS-Tournament SHALL mantener sus endpoints REST y eventos RabbitMQ sin cambios
5. THE MS-Game SHALL mantener sus endpoints REST excepto la integración de storage
6. THE BFF-Player y BFF-Organizer SHALL mantener sus contratos de API hacia frontends sin cambios
7. THE eventos RabbitMQ (user.registered, game.finished, etc.) SHALL mantener su estructura de payload sin cambios

---

### Requirement 11: MS-Auth Decommissioning

**User Story:** Como arquitecto del sistema, quiero remover MS-Auth de la arquitectura, para que no haya servicios redundantes después de la migración.

#### Acceptance Criteria

1. WHEN la migración a Supabase Auth está completa, THE docker-compose.yml SHALL remover el servicio ms-auth
2. WHEN la migración a Supabase Auth está completa, THE docker-compose.yml SHALL remover la base de datos auth_db
3. THE API_Gateway SHALL remover todas las llamadas HTTP a MS-Auth (GET /auth/validate)
4. THE código fuente de MS-Auth SHALL ser archivado en un branch git separado antes de ser removido de main
5. THE documentación (CONTEXT.md, README.md) SHALL ser actualizada para reflejar que Supabase Auth reemplaza a MS-Auth
6. THE endpoints /auth/login, /auth/register, /auth/refresh y /auth/logout SHALL ser removidos del API Gateway routing

---

### Requirement 12: MinIO Decommissioning

**User Story:** Como arquitecto del sistema, quiero remover MinIO de la arquitectura, para que Supabase Storage sea el único servicio de almacenamiento de archivos.

#### Acceptance Criteria

1. WHEN la migración a Supabase Storage está completa, THE docker-compose.yml SHALL remover el servicio minio
2. THE MS-Game SHALL remover todas las dependencias de MinIO Client (io.minio:minio) del pom.xml
3. THE MS-Game SHALL remover las clases MinioStorageService y MinioConfig del código fuente
4. IF existen archivos PGN en MinIO, THEN THE equipo de desarrollo SHALL migrar los archivos a Supabase Storage antes de descomisionar MinIO
5. THE variables de entorno MINIO_URL, MINIO_ACCESS_KEY y MINIO_SECRET_KEY SHALL ser removidas de todos los archivos de configuración
6. THE documentación SHALL ser actualizada para indicar que Supabase Storage reemplaza a MinIO

---

### Requirement 13: Integration Testing

**User Story:** Como QA engineer, quiero ejecutar tests de integración end-to-end, para que pueda verificar que todos los flujos funcionales operan correctamente después de la migración.

#### Acceptance Criteria

1. WHEN un test ejecuta el flujo de registro, THE test SHALL verificar que el usuario se crea en Supabase Auth y puede autenticarse
2. WHEN un test ejecuta el flujo de login, THE test SHALL verificar que recibe access token y refresh token válidos
3. WHEN un test ejecuta el flujo de refresh token, THE test SHALL verificar que obtiene un nuevo access token sin re-autenticarse
4. WHEN un test ejecuta el flujo de subida de PGN, THE test SHALL verificar que el archivo se almacena en Supabase Storage y se genera presigned URL válida
5. WHEN un test ejecuta el flujo de descarga de PGN, THE test SHALL verificar que puede descargar el archivo usando la presigned URL
6. WHEN un test ejecuta el flujo de validación JWT en API Gateway, THE test SHALL verificar que headers X-User-Id, X-User-Email y X-User-Role se propagan correctamente
7. WHEN un test ejecuta el flujo completo de demo (registro → login → crear torneo → jugar partida → subir PGN), THE test SHALL completar sin errores

---

### Requirement 14: Documentation and Setup Instructions

**User Story:** Como nuevo desarrollador en el equipo, quiero documentación clara sobre cómo configurar Supabase, para que pueda levantar el entorno de desarrollo rápidamente.

#### Acceptance Criteria

1. THE proyecto SHALL incluir un archivo docs/SUPABASE_SETUP.md con instrucciones paso a paso para instalar Supabase CLI
2. THE docs/SUPABASE_SETUP.md SHALL documentar cómo inicializar Supabase Local con `supabase init` y `supabase start`
3. THE docs/SUPABASE_SETUP.md SHALL documentar cómo obtener las API keys (anon key, service key) y JWT secret de Supabase Local
4. THE docs/SUPABASE_SETUP.md SHALL documentar cómo crear el bucket chessquery-pgn y configurar RLS policies
5. THE docs/SUPABASE_SETUP.md SHALL documentar cómo crear la tabla user_profiles y sus constraints
6. THE README.md SHALL ser actualizado con la nueva arquitectura (8 componentes en lugar de 11)
7. THE CONTEXT.md SHALL ser actualizado para reemplazar referencias a MS-Auth con Supabase Auth y MinIO con Supabase Storage

---

### Requirement 15: CI/CD Pipeline Compatibility

**User Story:** Como DevOps engineer, quiero que la migración a Supabase sea compatible con GitHub Actions y futuro deployment en AWS, para que el pipeline CI/CD no se vea afectado negativamente.

#### Acceptance Criteria

1. THE GitHub Actions workflow SHALL poder ejecutar tests usando Supabase Local mediante `supabase start` en el runner
2. THE GitHub Actions workflow SHALL poder construir frontends con variables VITE_SUPABASE_URL y VITE_SUPABASE_ANON_KEY desde secrets
3. THE GitHub Actions workflow SHALL poder construir microservices Java sin dependencias de MS-Auth ni MinIO
4. THE deployment a AWS SHALL poder usar Supabase Cloud (Pro plan) como servicio externo sin requerir self-hosting
5. THE deployment a AWS SHALL reducir la cantidad de servicios en ECS de 9 a 7 microservices
6. THE .github/workflows SHALL ser actualizado para remover pasos de build y deploy de MS-Auth
7. THE pipeline CI/CD SHALL completar en menos tiempo que antes de la migración debido a menos servicios que construir

---

### Requirement 16: Performance and Scalability

**User Story:** Como arquitecto del sistema, quiero que la migración a Supabase mantenga o mejore el performance actual, para que la experiencia de usuario no se degrade.

#### Acceptance Criteria

1. WHEN un usuario se autentica, THE Supabase_Auth SHALL responder en menos de 500ms (p95)
2. WHEN API Gateway valida un JWT, THE validación SHALL completar en menos de 50ms (validación local sin llamadas de red)
3. WHEN MS-Game sube un archivo PGN, THE Supabase_Storage SHALL aceptar el archivo en menos de 2 segundos para archivos de hasta 1MB
4. WHEN un usuario descarga un PGN usando presigned URL, THE Supabase_Storage SHALL servir el archivo con latencia menor a 200ms (p95)
5. THE Supabase_Auth SHALL soportar al menos 100 autenticaciones concurrentes sin degradación
6. THE Supabase_Storage SHALL soportar al menos 50 subidas concurrentes de archivos PGN sin degradación
7. THE API_Gateway SHALL mantener throughput de al menos 1000 requests/segundo con validación JWT de Supabase

---

### Requirement 17: Security and Data Protection

**User Story:** Como security engineer, quiero que la migración a Supabase mantenga los estándares de seguridad actuales, para que no se introduzcan vulnerabilidades.

#### Acceptance Criteria

1. THE Supabase_Auth SHALL hashear passwords usando bcrypt con factor de costo mínimo de 10
2. THE JWT secret SHALL tener al menos 32 caracteres y ser generado aleatoriamente
3. THE Service_Key SHALL ser almacenado solo en variables de entorno backend y nunca expuesto a frontends
4. THE Anon_Key SHALL ser la única key expuesta a frontends y tener permisos limitados por RLS policies
5. THE Supabase_Storage SHALL aplicar RLS policies para prevenir acceso no autorizado a archivos
6. THE API_Gateway SHALL validar que JWT no ha sido manipulado verificando la firma con JWT secret
7. THE Supabase_Auth SHALL implementar rate limiting para prevenir ataques de fuerza bruta (máximo 20 intentos de login por IP por minuto)

---

### Requirement 18: Error Handling and Logging

**User Story:** Como desarrollador, quiero que errores de Supabase sean manejados apropiadamente, para que pueda diagnosticar problemas rápidamente.

#### Acceptance Criteria

1. WHEN Supabase_Auth retorna un error, THE API_Gateway SHALL loguear el error con nivel ERROR incluyendo timestamp y request ID
2. WHEN Supabase_Storage retorna un error, THE MS-Game SHALL loguear el error con nivel ERROR incluyendo gameId y storage key
3. IF Supabase_Auth está inaccesible, THEN THE API_Gateway SHALL retornar 503 Service Unavailable con mensaje descriptivo
4. IF Supabase_Storage está inaccesible, THEN THE MS-Game SHALL retornar 503 Service Unavailable y NO persistir el registro de GAME
5. THE API_Gateway SHALL retornar mensajes de error en formato estándar: {status, error, message, timestamp}
6. THE MS-Game SHALL retornar mensajes de error en formato estándar cuando operaciones de storage fallan
7. THE logs SHALL NO incluir tokens JWT completos, passwords ni API keys (solo primeros 8 caracteres para debugging)

---

### Requirement 19: Monitoring and Health Checks

**User Story:** Como SRE, quiero monitorear la salud de Supabase y sus integraciones, para que pueda detectar problemas proactivamente.

#### Acceptance Criteria

1. THE API_Gateway SHALL exponer un endpoint GET /health que verifica conectividad con Supabase Auth
2. THE MS-Game SHALL exponer un endpoint GET /health que verifica conectividad con Supabase Storage
3. WHEN Supabase_Auth está inaccesible, THE API_Gateway health check SHALL retornar status "DOWN" con detalles del error
4. WHEN Supabase_Storage está inaccesible, THE MS-Game health check SHALL retornar status "DOWN" con detalles del error
5. THE health checks SHALL completar en menos de 5 segundos o timeout
6. THE docker-compose.yml SHALL configurar healthchecks para servicios que dependen de Supabase
7. THE health checks SHALL NO exponer información sensible (API keys, secrets) en las respuestas

---

### Requirement 20: Rollback Strategy

**User Story:** Como tech lead, quiero tener una estrategia de rollback clara, para que pueda revertir la migración si se encuentran problemas críticos.

#### Acceptance Criteria

1. THE código de MS-Auth SHALL ser preservado en un branch git llamado "backup/ms-auth-pre-supabase" antes de ser removido
2. THE configuración de MinIO en docker-compose.yml SHALL ser preservada en un archivo docker-compose.backup.yml
3. THE equipo SHALL documentar en docs/ROLLBACK.md los pasos exactos para revertir a MS-Auth y MinIO
4. THE docs/ROLLBACK.md SHALL incluir scripts SQL para migrar datos de Supabase Auth de vuelta a auth_db si es necesario
5. THE docs/ROLLBACK.md SHALL incluir comandos para restaurar archivos PGN de Supabase Storage a MinIO
6. WHEN se ejecuta rollback, THE sistema SHALL poder volver a estado funcional en menos de 1 hora
7. THE rollback SHALL ser posible sin pérdida de datos de usuarios ni archivos PGN creados durante la migración

---

## Notes

### Parser and Serializer Requirements

Este spec no incluye parsers ni serializers custom, ya que:
- Supabase Auth maneja serialización de JWT internamente
- Supabase Storage maneja archivos PGN como blobs binarios sin parsing
- Los microservicios custom mantienen sus parsers existentes sin cambios

### Property-Based Testing Guidance

Los siguientes acceptance criteria son candidatos para property-based testing:

**Requirement 3 (JWT Validation):**
- Criterio 1: Generar 100 JWT válidos con diferentes claims y verificar que todos son validados correctamente
- Criterio 4: Generar 100 JWT inválidos (firma incorrecta, expirados, malformados) y verificar que todos son rechazados

**Requirement 6 (PGN File Storage):**
- Criterio 2: Generar 100 rutas de archivo con diferentes años, meses y gameIds y verificar que todas se construyen correctamente
- Criterio 7: Generar archivos PGN de tamaños variados (1KB - 2MB) y verificar que archivos >1MB son rechazados

**Requirement 7 (Presigned URL):**
- Criterio 2: Generar 100 presigned URLs y verificar que todas expiran después de 1 hora
- Criterio 5: Generar presigned URLs para diferentes keys y verificar que el formato es consistente

**Requirement 16 (Performance):**
- Criterio 2: Validar 1000 JWT diferentes y verificar que todas las validaciones completan en <50ms
- Criterio 6: Subir 100 archivos PGN concurrentemente y verificar que no hay degradación

Los siguientes NO son candidatos para PBT (usar integration tests):
- Requirement 1: Setup de infraestructura (ejecutar una vez)
- Requirement 13: Tests E2E (usar 2-3 escenarios representativos)
- Requirement 19: Health checks (comportamiento determinístico)

### Migration Timeline

**Semana 1:** Requirements 1-5, 8-9 (Setup Supabase, Auth migration)
**Semana 2:** Requirements 6-7, 10-12 (Storage migration, decommissioning)
**Testing:** Requirements 13, 16-19 (Integration, performance, security)
**Documentation:** Requirements 14, 20 (Docs, rollback strategy)

### Success Criteria

La migración se considera exitosa cuando:
1. Todos los flujos funcionales de la demo operan sin MS-Auth ni MinIO
2. Tests de integración E2E pasan al 100%
3. Performance es igual o mejor que arquitectura actual
4. Documentación está completa y validada por nuevo desarrollador
5. Estrategia de rollback está documentada y probada

