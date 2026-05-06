# Implementation Plan — Supabase Migration

## Objetivo
Migrar MS-Auth y MinIO a Supabase Auth y Supabase Storage respectivamente, reduciendo los componentes de 11 a 8 (-27%). La migración es incremental en 4 fases, cada una independiente y reversible.

---

## Fase 1: Supabase Local Development Environment

- [ ] 1. Instalar y configurar Supabase CLI
  - Instalar Supabase CLI localmente (`brew install supabase/tap/supabase` o `npm i -g supabase`)
  - Ejecutar `supabase init` en la raíz del proyecto para crear la carpeta `supabase/`
  - Verificar que se genera `supabase/config.toml` con configuración por defecto
  - Ejecutar `supabase start` y verificar que los servicios levantan:
    - Auth API en http://localhost:54321/auth/v1
    - Storage API en http://localhost:54321/storage/v1
    - Studio UI en http://localhost:54323
  - Documentar las API keys generadas (anon key, service key, JWT secret)
  - Verificar que no hay conflictos de puertos con docker-compose.yml existente
  - Ejecutar `supabase stop` y verificar que los contenedores se detienen correctamente
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6_
  - **Commit:** `feat: instalar y configurar Supabase CLI localmente`

- [ ] 2. Crear migraciones SQL (user_profiles + storage bucket)
  - Crear `supabase/migrations/00001_create_user_profiles.sql`:
    - Tabla `public.user_profiles` con columnas: id (UUID PK FK→auth.users ON DELETE CASCADE), role (TEXT CHECK IN PLAYER/ORGANIZER/ADMIN), created_at (TIMESTAMPTZ DEFAULT NOW())
    - Habilitar RLS en user_profiles
    - Policy: "Users can read own profile" (SELECT WHERE auth.uid() = id)
    - Policy: "Service role can manage all profiles" (ALL WHERE auth.role() = 'service_role')
    - Trigger `on_auth_user_created`: auto-insertar registro en user_profiles al crear usuario
    - Función `handle_new_user()`: extraer role de raw_user_meta_data, default 'PLAYER'
  - Crear `supabase/migrations/00002_create_storage_bucket.sql`:
    - Insertar bucket 'chessquery-pgn' en storage.buckets (public=false)
    - Policy: "Authenticated users can upload PGN" (INSERT WHERE bucket_id AND authenticated)
    - Policy: "Service role can manage all PGN files" (ALL WHERE bucket_id AND service_role)
  - Ejecutar `supabase db reset` para aplicar migraciones
  - Verificar via Studio UI que la tabla y bucket existen
  - Configurar webhook en `supabase/config.toml`:
    ```toml
    [auth.hook.send_email]
    enabled = false

    [auth.hook.custom_access_token]
    enabled = false

    [auth.hook.send_sms]
    enabled = false

    # Webhook para user.registered
    [auth.hook.custom_access_token]
    enabled = true
    uri = "http://host.docker.internal:8080/webhooks/supabase/user-registered"
    secrets = "${SUPABASE_WEBHOOK_SECRET}"
    ```
  - Verificar que el webhook se dispara al crear usuario en Studio UI
  - **NOTA:** Usar `host.docker.internal` para que Supabase Local (Docker) pueda alcanzar el API Gateway en el host
  - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 6.1, 6.6_
  - **Commit:** `feat: crear migraciones SQL para user_profiles y storage bucket`

- [ ] 3. Configurar variables de entorno y .env.example
  - Crear/actualizar `.env.example` con todas las variables de Supabase:
    - `SUPABASE_URL`, `SUPABASE_JWT_SECRET`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_KEY`
    - `SUPABASE_WEBHOOK_SECRET`
    - `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY`
    - `STORAGE_PROVIDER=supabase`
  - Generar `SUPABASE_WEBHOOK_SECRET`:
    ```bash
    openssl rand -base64 32
    ```
  - Agregar a `.env`:
    ```env
    SUPABASE_WEBHOOK_SECRET=<valor-generado>
    ```
  - Verificar que `.env` está en `.gitignore`
  - Crear `.env` local con valores de `supabase start`
  - Actualizar `docker-compose.yml` para inyectar variables de Supabase a api-gateway y ms-game
  - Agregar a docker-compose.yml (servicio api-gateway):
    ```yaml
    environment:
      - SUPABASE_WEBHOOK_SECRET=${SUPABASE_WEBHOOK_SECRET}
    ```
  - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7_
  - **Commit:** `feat: configurar variables de entorno para Supabase`

---

## Fase 2: Autenticación con Supabase Auth

- [ ] 4. Migrar validación JWT en API Gateway
  - Agregar dependencia `io.jsonwebtoken:jjwt-api` (>= 0.11.5) al pom.xml del API Gateway
  - Agregar dependencias de implementación: `jjwt-impl`, `jjwt-jackson`
  - Crear clase `SupabaseJwtAuthFilter` que:
    - Extrae token del header Authorization: Bearer {token}
    - Valida firma del JWT usando `SUPABASE_JWT_SECRET` (validación local, sin llamadas HTTP)
    - Extrae claims: sub → X-User-Id (UUID), email → X-User-Email
    - Extrae role de user_metadata → X-User-Role
    - Si user_metadata.role no existe, consulta user_profiles como fallback
    - Retorna 401 si JWT es inválido, expirado, o ausente
  - Configurar `application.yml` con propiedad `supabase.jwt-secret: ${SUPABASE_JWT_SECRET}`
  - Remover/desactivar el filtro actual que llama a `GET /auth/validate` en MS-Auth
  - Escribir tests unitarios:
    - JWT válido → extrae X-User-Id, X-User-Email, X-User-Role
    - JWT expirado → 401
    - JWT con firma inválida → 401
    - Request sin Authorization → 401
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_
  - **Commit:** `feat: migrar validación JWT en API Gateway a Supabase`

- [ ] 5. Implementar webhook user.registered en API Gateway
  - Crear `SupabaseWebhookController` con endpoint `POST /webhooks/supabase/user-registered`
  - **NOTA IMPORTANTE:** Este endpoint NO requiere validación JWT (es llamado por Supabase directamente). Validar SOLO el header `X-Supabase-Webhook-Secret` para autenticación
  - Implementar validación de `X-Supabase-Webhook-Secret` header
  - Construir evento `user.registered` con formato existente de CONTEXT.md:
    - eventId (UUID v4), eventType ("user.registered"), timestamp
    - payload: userId (UUID de Supabase), email, firstName, lastName, role
  - Publicar evento a exchange `ChessEvents` con routing key `user.registered` via RabbitTemplate
  - Crear `SupabaseWebhookPayload` DTO para deserializar el payload del webhook
  - Configurar webhook en `supabase/config.toml`:
    - Trigger: INSERT en auth.users
    - URL: http://api-gateway:8080/webhooks/supabase/user-registered
    - Header: X-Supabase-Webhook-Secret
  - Escribir tests:
    - Webhook con secret válido → publica evento a RabbitMQ
    - Webhook con secret inválido → 401
    - Verificar formato del evento publicado
  - _Requirements: 2.4, 10.7_
  - **Commit:** `feat: implementar webhook user.registered en API Gateway`

- [ ] 6. Migración SQL: columna supabase_user_id en PLAYER
  - Crear script de migración SQL para user_db:
    - Ubicación: `ms-users/src/main/resources/db/migration/V002__add_supabase_user_id.sql`
    - Contenido:
      ```sql
      ALTER TABLE player ADD COLUMN supabase_user_id UUID;
      CREATE UNIQUE INDEX idx_player_supabase_user_id ON player(supabase_user_id);
      ```
    - **NOTA:** Esta migración se ejecuta automáticamente por Flyway/Liquibase al iniciar MS-Users
  - Actualizar entidad JPA `Player` en MS-Users:
    - Agregar campo `supabaseUserId` (UUID) con `@Column(name = "supabase_user_id")`
  - Actualizar `PlayerRepository` con métodos:
    - `Optional<Player> findBySupabaseUserId(UUID supabaseUserId)`
    - `Optional<Player> findByEmail(String email)` (si no existe)
  - Crear endpoint `GET /users/by-supabase-id/{supabaseUserId}` en MS-Users:
    - Busca PLAYER por supabase_user_id
    - Retorna PlayerProfileDTO o 404
  - Actualizar handler de evento `user.registered` para guardar supabase_user_id
  - Escribir tests:
    - Crear player con supabase_user_id → persistido correctamente
    - Buscar por supabase_user_id → retorna player correcto
    - Buscar UUID inexistente → 404
  - _Requirements: 10.3, 10.2_
  - **Commit:** `feat: agregar columna supabase_user_id y endpoint de resolución en MS-Users`

- [ ] 7. Integrar Supabase Client en chess-portal
  - Instalar `@supabase/supabase-js` (>= 2.x) en chess-portal
  - Crear `src/lib/supabase.ts` con inicialización del cliente:
    - `createClient(VITE_SUPABASE_URL, VITE_SUPABASE_ANON_KEY)`
  - Actualizar flujo de registro (`/register`):
    - Reemplazar POST a `/auth/register` con `supabase.auth.signUp({ email, password, options: { data: { role, firstName, lastName } } })`
  - Actualizar flujo de login (`/login`):
    - Reemplazar POST a `/auth/login` con `supabase.auth.signInWithPassword({ email, password })`
  - Actualizar gestión de tokens:
    - Access token en memoria, refresh token gestionado por Supabase Client
    - Configurar `onAuthStateChange()` para actualizar UI
  - Actualizar logout:
    - Reemplazar POST a `/auth/logout` con `supabase.auth.signOut()`
  - Actualizar interceptor/middleware de requests API:
    - Obtener token via `supabase.auth.getSession()` antes de cada request
  - Verificar que flujo completo funciona: register → login → dashboard → logout
  - _Requirements: 4.1, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8_
  - **Commit:** `feat: integrar Supabase Client en chess-portal`

- [ ] 8. Integrar Supabase Client en organizer-panel
  - Instalar `@supabase/supabase-js` (>= 2.x) en organizer-panel
  - Crear `src/lib/supabase.ts` (misma configuración que chess-portal)
  - Actualizar flujos de auth (register con role ORGANIZER, login, logout)
  - Configurar `onAuthStateChange()` para actualizar UI
  - Actualizar interceptor de requests API para usar token de Supabase
  - Verificar flujo completo: register → login → gestión de torneos → logout
  - _Requirements: 4.2_
  - **Commit:** `feat: integrar Supabase Client en organizer-panel`

- [ ] 9. Configurar RLS y roles en Supabase
  - Verificar que RLS policies de user_profiles funcionan correctamente:
    - Usuario puede leer su propio perfil → SELECT funciona
    - Usuario no puede leer perfil de otro → SELECT retorna vacío
    - Service role puede gestionar todos los perfiles → CRUD completo
  - Verificar que el role se incluye en el JWT como user_metadata
  - Verificar que API Gateway propaga X-User-Role correctamente
  - Test de actualización de role por admin:
    - Admin actualiza role en user_profiles → próximo JWT refleja el cambio
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_
  - **Commit:** `feat: configurar y verificar RLS policies en Supabase`

---

## Fase 3: PGN Storage con Supabase Storage

- [ ] 10. Crear interfaz StorageService y SupabaseStorageService
  - Crear interfaz `StorageService` con métodos:
    - `String uploadPgn(String key, byte[] pgnContent)`
    - `String generatePresignedUrl(String key)`
  - Refactorizar MinIO storage actual para implementar `StorageService` (si no lo hace ya)
  - Crear `SupabaseStorageService` implementando `StorageService`:
    - `uploadPgn`: POST a `{SUPABASE_URL}/storage/v1/object/chessquery-pgn/{key}`
      - Header: `Authorization: Bearer {SERVICE_KEY}`, `Content-Type: application/x-chess-pgn`
    - `generatePresignedUrl`: POST a `{SUPABASE_URL}/storage/v1/object/sign/chessquery-pgn/{key}`
      - Body: `{"expiresIn": 3600}` (1 hora)
      - Retorna URL completa: `{SUPABASE_URL}/storage/v1{signedURL}`
  - Configurar selección de provider por variable de entorno:
    - Crear clase `StorageConfig` con `@Configuration`:
      ```java
      @Configuration
      public class StorageConfig {

          @Bean
          @ConditionalOnProperty(name = "storage.provider", havingValue = "supabase")
          public StorageService supabaseStorageService(
              RestTemplate restTemplate,
              @Value("${supabase.url}") String supabaseUrl,
              @Value("${supabase.service-key}") String serviceKey) {
              return new SupabaseStorageService(restTemplate, supabaseUrl, serviceKey);
          }

          @Bean
          @ConditionalOnProperty(name = "storage.provider", havingValue = "minio")
          public StorageService minioStorageService(MinioClient minioClient) {
              return new MinioStorageService(minioClient);
          }
      }
      ```
    - Agregar a `application.yml`:
      ```yaml
      storage:
        provider: ${STORAGE_PROVIDER:supabase}
      ```
  - Escribir tests:
    - Upload PGN → almacena correctamente en Supabase Storage
    - Generate presigned URL → URL válida con expiración 1h
    - Upload archivo >1MB → rechazado (413)
    - Download via presigned URL → archivo accesible
  - _Requirements: 6.2, 6.3, 6.4, 6.5, 6.7, 7.1, 7.2, 7.3, 7.4, 7.5_
  - **Commit:** `feat: crear SupabaseStorageService para almacenamiento PGN`

- [ ] 11. Migrar MS-Game a Supabase Storage
  - Reemplazar inyección de MinioClient por StorageService en GameService
  - Actualizar lógica de `POST /games`:
    - Usar `storageService.uploadPgn(key, pgnContent)` en lugar de MinioClient
    - Key mantiene formato: `games/{year}/{month}/{gameId}.pgn`
    - Almacenar key en `GAME.pgn_storage_key` (sin cambios en el campo)
  - Actualizar lógica de `GET /games/{id}/pgn-url`:
    - Usar `storageService.generatePresignedUrl(key)` en lugar de MinioClient
  - Agregar manejo de errores:
    - Si Supabase Storage inaccesible → 503 Service Unavailable
    - NO persistir GAME si upload falla
    - Loguear errores con gameId y storage key (sin exponer tokens)
  - Verificar flujo completo:
    - POST /games → PGN subido a Supabase Storage
    - GET /games/{id}/pgn-url → presigned URL funcional
    - Download PGN via presigned URL → archivo correcto
  - _Requirements: 6.2, 6.3, 6.4, 6.5, 7.1, 7.2, 7.3, 18.2, 18.4, 18.6_
  - **Commit:** `feat: migrar MS-Game a Supabase Storage`

---

## Fase 4: Descomisionamiento y Documentación

- [ ] 12. Archivar MS-Auth y configuración de MinIO
  - Crear branch `backup/ms-auth-pre-supabase` con el estado actual de MS-Auth
  - Crear archivo `docker-compose.backup.yml` con la configuración de MinIO
  - Verificar que el branch y backup son accesibles
  - _Requirements: 20.1, 20.2, 11.4_
  - **Commit:** `feat: archivar MS-Auth en branch backup y preservar config MinIO`

- [ ] 13. Remover MS-Auth de la arquitectura
  - Remover servicio `ms-auth` de docker-compose.yml
  - Remover servicio `auth_db` de docker-compose.yml
  - Remover rutas de auth (`/auth/login`, `/auth/register`, `/auth/refresh`, `/auth/logout`) del API Gateway routing
  - Remover configuración de WebClient que llama a MS-Auth
  - Remover variables de entorno de MS-Auth del docker-compose.yml
  - Verificar que el sistema funciona sin MS-Auth:
    - Register via Supabase → login → API calls → todo funcional
  - _Requirements: 11.1, 11.2, 11.3, 11.5, 11.6_
  - **Commit:** `feat: remover MS-Auth y auth_db de la arquitectura`

- [ ] 14. Remover MinIO de la arquitectura
  - Remover servicio `minio` de docker-compose.yml
  - NO remover dependencia `io.minio:minio` del pom.xml (marcar como `<optional>true</optional>`)
  - NO remover clases MinioStorageService, MinioConfig (mover a paquete `legacy` o anotar con `@Deprecated`)
  - Remover variables de entorno MINIO_URL, MINIO_ACCESS_KEY, MINIO_SECRET_KEY de docker-compose.yml (preservar en `docker-compose.backup.yml`)
  - Cambiar `STORAGE_PROVIDER` default a `supabase` en `application.yml`
  - Verificar que upload/download PGN funciona sin MinIO
  - **NOTA:** Código de MinIO se preserva para facilitar rollback según `docs/ROLLBACK.md`
  - _Requirements: 12.1, 12.5_
  - **Commit:** `feat: remover MinIO de la arquitectura`

- [ ] 15. Actualizar health checks y monitoreo
  - Actualizar API Gateway health check: verificar conectividad con Supabase Auth
  - Actualizar MS-Game health check: verificar conectividad con Supabase Storage
  - Configurar healthchecks en docker-compose.yml para servicios que dependen de Supabase
  - Verificar que health checks:
    - Retornan "UP" cuando Supabase está disponible
    - Retornan "DOWN" con detalles cuando Supabase está inaccesible
    - Completan en <5 segundos
    - NO exponen secrets en respuestas
  - _Requirements: 19.1, 19.2, 19.3, 19.4, 19.5, 19.6, 19.7_
  - **Commit:** `feat: actualizar health checks para Supabase`

- [ ] 16. Actualizar documentación
  - Actualizar `CONTEXT.md`:
    - Reemplazar sección de MS-Auth con Supabase Auth
    - Reemplazar sección de MinIO con Supabase Storage
    - Actualizar URLs internas Docker (remover ms-auth:9090, minio:9000)
    - Actualizar sección de autenticación (flujo con Supabase)
    - Actualizar diagrama de arquitectura (8 componentes)
  - Actualizar `README.md`:
    - Nueva arquitectura (8 componentes en lugar de 11)
    - Instrucciones de setup con Supabase
  - Crear `docs/SUPABASE_SETUP.md`:
    - Instrucciones paso a paso para instalar Supabase CLI
    - Cómo inicializar con `supabase init` y `supabase start`
    - Cómo obtener API keys y JWT secret
    - Cómo crear bucket y configurar RLS policies
    - Cómo crear tabla user_profiles
  - Crear `docs/ROLLBACK.md`:
    - Pasos exactos para revertir a MS-Auth y MinIO
    - Scripts SQL para migrar datos de vuelta
    - Comandos para restaurar archivos PGN
    - Tiempo estimado: <1 hora
  - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5, 14.6, 14.7, 20.3, 20.4, 20.5_
  - **Commit:** `docs: actualizar documentación con arquitectura Supabase`

---

## Fase 5: Testing y Validación

- [ ] 17. Tests de integración E2E
  - Crear script de smoke test que ejecute flujo completo contra Supabase Local:
    1. Register usuario PLAYER → verificar creación en Supabase Auth y user_profiles
    2. Verificar que evento `user.registered` se publica a RabbitMQ:
       - Verificar formato del evento (eventId, eventType, timestamp, payload)
       - Verificar que payload contiene userId (UUID), email, role
       - Verificar que el evento llega a la cola correcta
    3. Login → verificar recepción de access token y refresh token
    4. Refresh token → verificar nuevo access token sin re-autenticación
    5. API call con token → verificar propagación de X-User-Id, X-User-Email, X-User-Role
    6. Upload PGN → verificar almacenamiento en Supabase Storage
    7. Generate presigned URL → verificar accesibilidad del archivo
    8. Download PGN via presigned URL → verificar contenido
    9. Flujo completo demo: register → login → crear torneo → jugar partida → subir PGN
  - Verificar que todos los flujos completan sin errores
  - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5, 13.6, 13.7_
  - **Commit:** `test: crear tests de integración E2E para Supabase`

- [ ] 18. Verificación de seguridad
  - Verificar que passwords se hashean con bcrypt (factor ≥ 10) en Supabase Auth
  - Verificar que JWT secret tiene ≥ 32 caracteres
  - Verificar que Service Key solo está en variables backend, no en frontends
  - Verificar que Anon Key tiene permisos limitados por RLS
  - Verificar que RLS previene acceso no autorizado a archivos y perfiles
  - Verificar que logs NO incluyen tokens completos, passwords, ni API keys
  - Verificar rate limiting en Supabase Auth
  - _Requirements: 17.1, 17.2, 17.3, 17.4, 17.5, 17.6, 17.7, 18.7_
  - **Commit:** `test: verificar seguridad de la integración Supabase`

- [ ] 19. Tests de performance
  - Benchmark de validación JWT en API Gateway:
    - Validar 1000 JWT diferentes
    - Verificar que todas completan en <50ms (p95)
  - Benchmark de Supabase Auth:
    - 100 autenticaciones concurrentes
    - Verificar latencia <500ms (p95)
  - Benchmark de Supabase Storage:
    - 50 uploads concurrentes de archivos PGN (100KB-1MB)
    - Verificar latencia <2s por archivo
  - Comparar con performance de MS-Auth + MinIO (baseline si está disponible)
  - Documentar resultados en `docs/PERFORMANCE.md`
  - _Requirements: 16.1, 16.2, 16.3, 16.4, 16.5, 16.6, 16.7_
  - **Commit:** `test: validar performance de Supabase vs baseline`

- [ ] 20. Checkpoint final
  - Verificar que el sistema funciona con 8 componentes (sin MS-Auth, sin MinIO, sin auth_db)
  - Verificar que todos los flujos de demo operan correctamente
  - Verificar que la documentación está completa y actualizada
  - Verificar que la estrategia de rollback está documentada
  - Verificar que no hay secrets expuestos en código ni en logs
  - Ejecutar smoke test completo
  - Confirmar que tests de integración pasan al 100%
  - _Requirements: todos_
  - **Commit:** `test: checkpoint final de migración Supabase`
