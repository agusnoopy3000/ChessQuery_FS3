# Supabase Migration Design

## Overview

ChessQuery actualmente opera con 11 componentes incluyendo MS-Auth (Spring Boot), MinIO (object storage), y múltiples bases de datos PostgreSQL. Esta migración parcial reemplaza MS-Auth y MinIO con servicios gestionados de Supabase, reduciendo la complejidad operativa de 11 a 8 componentes (-27%).

**Estrategia:** Migración incremental en 4 fases — primero el entorno local, luego autenticación, storage, y finalmente descomisionamiento de servicios obsoletos. Cada fase es independiente y reversible.

**Principio clave:** Los microservicios custom (MS-Users, MS-Tournament, MS-Game, MS-ETL, MS-Notifications) y BFFs mantienen su lógica de negocio intacta. Solo se modifica la capa de autenticación (API Gateway) y storage (MS-Game).

### Arquitectura Actual vs. Propuesta

```
ACTUAL (11 componentes):
Frontend → API Gateway → MS-Auth (validar JWT)
                       → MS-Users, MS-Tournament, MS-Game, MS-ETL, MS-Notifications
                       → BFF-Player, BFF-Organizer
MS-Game → MinIO (PGN files)
PostgreSQL: auth_db, user_db, tournament_db, game_db, etl_db, notif_db

PROPUESTA (8 componentes):
Frontend → Supabase Auth (login/register directo)
         → API Gateway (validar JWT Supabase localmente)
                      → MS-Users, MS-Tournament, MS-Game, MS-ETL, MS-Notifications
                      → BFF-Player, BFF-Organizer
MS-Game → Supabase Storage (PGN files)
PostgreSQL: user_db, tournament_db, game_db, etl_db, notif_db
Supabase: auth (gestionado), storage (gestionado), user_profiles
```

**Componentes eliminados:** MS-Auth, MinIO, auth_db
**Componentes agregados:** Supabase Auth, Supabase Storage, Supabase DB (user_profiles)

---

## Glossary

- **Supabase Local**: Instancia de Supabase que corre localmente via Docker CLI (`supabase start`), incluye Auth, Storage, PostgreSQL, Studio
- **JWT Secret**: Clave secreta compartida para firmar/verificar JWT entre Supabase Auth y API Gateway
- **Service Key**: API key con privilegios de admin, usada solo por backends (MS-Game) para operaciones de storage
- **Anon Key**: API key pública de Supabase, usada por frontends para operaciones limitadas por RLS
- **RLS (Row Level Security)**: Políticas de seguridad PostgreSQL que restringen acceso a filas según el usuario autenticado
- **user_profiles**: Tabla custom en Supabase DB que almacena roles y metadata adicional de usuarios
- **Presigned URL**: URL temporal firmada para acceso directo a archivos en Supabase Storage sin autenticación

---

## Detailed Design

### Fase 1: Supabase Local Development Environment

**Validates: Requirements 1, 9**

#### Configuración del entorno

Supabase CLI se instala localmente y se ejecuta con `supabase init` + `supabase start`. Esto levanta contenedores Docker con:
- **Auth**: http://localhost:54321/auth/v1
- **Storage**: http://localhost:54321/storage/v1
- **REST API**: http://localhost:54321/rest/v1
- **Studio**: http://localhost:54323

#### Integración con docker-compose existente

Supabase Local corre como stack independiente (puertos 54321-54323). No modifica el `docker-compose.yml` existente. Los microservicios se comunican con Supabase via variables de entorno:

```
# .env (Supabase Local defaults)
SUPABASE_URL=http://localhost:54321
SUPABASE_JWT_SECRET=super-secret-jwt-token-with-at-least-32-characters-long
SUPABASE_ANON_KEY=eyJ...  # generado por supabase start
SUPABASE_SERVICE_KEY=eyJ... # generado por supabase start

# Frontend variables
VITE_SUPABASE_URL=http://localhost:54321
VITE_SUPABASE_ANON_KEY=eyJ...
```

#### Migraciones SQL

Las migraciones SQL se almacenan en `supabase/migrations/` y se ejecutan automáticamente al hacer `supabase start`. Incluyen:

```sql
-- supabase/migrations/00001_create_user_profiles.sql
CREATE TABLE public.user_profiles (
  id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  role TEXT NOT NULL CHECK (role IN ('PLAYER', 'ORGANIZER', 'ADMIN')),
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- RLS policies
ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can read own profile"
  ON public.user_profiles FOR SELECT
  USING (auth.uid() = id);

CREATE POLICY "Service role can manage all profiles"
  ON public.user_profiles FOR ALL
  USING (auth.role() = 'service_role');

-- Trigger: auto-insert profile on user registration
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO public.user_profiles (id, role)
  VALUES (NEW.id, COALESCE(NEW.raw_user_meta_data->>'role', 'PLAYER'));
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();
```

```sql
-- supabase/migrations/00002_create_storage_bucket.sql
INSERT INTO storage.buckets (id, name, public)
VALUES ('chessquery-pgn', 'chessquery-pgn', false);

-- RLS: autenticados pueden subir, cualquiera puede leer (via presigned URL)
CREATE POLICY "Authenticated users can upload PGN"
  ON storage.objects FOR INSERT
  WITH CHECK (bucket_id = 'chessquery-pgn' AND auth.role() = 'authenticated');

CREATE POLICY "Service role can manage all PGN files"
  ON storage.objects FOR ALL
  USING (bucket_id = 'chessquery-pgn' AND auth.role() = 'service_role');
```

---

### Fase 2: Autenticación con Supabase Auth

**Validates: Requirements 2, 3, 4, 5, 8**

#### Flujo de autenticación (nuevo)

```
1. Frontend → Supabase Auth: signUp(email, password, {role: 'PLAYER'})
   ← JWT access token (1h) + refresh token (7d)
   → Trigger crea user_profiles con role

2. Frontend → Supabase Auth: signInWithPassword(email, password)
   ← JWT access token + refresh token

3. Frontend → API Gateway: request con Authorization: Bearer {jwt}
   → Gateway valida JWT localmente (verificar firma con JWT_SECRET)
   → Gateway extrae claims: sub → X-User-Id, email → X-User-Email
   → Gateway consulta user_profiles.role → X-User-Role
   → Gateway propaga headers downstream

4. Frontend → Supabase Auth: signOut()
   → Supabase revoca sesión y refresh token
```

#### Cambios en API Gateway

El API Gateway actualmente llama a `GET /auth/validate` en MS-Auth para cada request. Con Supabase, la validación se hace localmente:

**Antes (MS-Auth):**
```java
// GatewayFilter - llama HTTP a MS-Auth
WebClient.get()
    .uri("http://ms-auth:9090/auth/validate")
    .header("Authorization", bearerToken)
    .retrieve()
    .bodyToMono(AuthResponse.class)
```

**Después (Supabase JWT local):**
```java
// GatewayFilter - validación local con JWT
import io.jsonwebtoken.Jwts;

@Value("${supabase.jwt-secret}")
private String jwtSecret;

// Validar firma y expiración localmente
Claims claims = Jwts.parserBuilder()
    .setSigningKey(jwtSecret.getBytes())
    .build()
    .parseClaimsJws(token)
    .getBody();

String userId = claims.getSubject(); // sub claim
String email = claims.get("email", String.class);

// Obtener role de user_metadata o consultar user_profiles
String role = extractRoleFromClaims(claims);

// Propagar headers downstream (mismo formato que antes)
exchange.getRequest().mutate()
    .header("X-User-Id", userId)
    .header("X-User-Email", email)
    .header("X-User-Role", role);
```

**Beneficio clave:** Validación local elimina la latencia de red (~5-20ms por request) y el punto de fallo de MS-Auth.

#### Extracción del Role

El role se incluye en el JWT de Supabase como parte de `user_metadata`:

```json
{
  "sub": "uuid-v4",
  "email": "jugador@email.com",
  "user_metadata": {
    "role": "PLAYER"
  },
  "exp": 1719936000
}
```

El Gateway extrae `user_metadata.role` y lo propaga como `X-User-Role`. Si el claim no existe, se consulta `user_profiles` como fallback.

#### Webhook: Evento user.registered

Actualmente MS-Auth publica `user.registered` a RabbitMQ cuando un usuario se registra. Con Supabase, este evento se emite mediante un webhook que Supabase invoca al crear un usuario.

**Implementación:**

1. **Endpoint en API Gateway:**

```java
@RestController
@RequestMapping("/webhooks/supabase")
public class SupabaseWebhookController {

    private final RabbitTemplate rabbitTemplate;

    @PostMapping("/user-registered")
    public ResponseEntity<Void> handleUserRegistered(
            @RequestBody SupabaseWebhookPayload payload,
            @RequestHeader("X-Supabase-Webhook-Secret") String webhookSecret) {

        // Validar webhook secret
        if (!webhookSecret.equals(expectedWebhookSecret)) {
            return ResponseEntity.status(401).build();
        }

        // Construir evento user.registered con formato existente
        UserRegisteredEvent event = UserRegisteredEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("user.registered")
            .timestamp(Instant.now().toString())
            .payload(UserRegisteredPayload.builder()
                .userId(payload.getRecord().getId()) // UUID de Supabase
                .email(payload.getRecord().getEmail())
                .role(payload.getRecord().getRawUserMetaData().get("role"))
                .firstName(payload.getRecord().getRawUserMetaData().get("firstName"))
                .lastName(payload.getRecord().getRawUserMetaData().get("lastName"))
                .build())
            .build();

        rabbitTemplate.convertAndSend("ChessEvents", "user.registered", event);
        return ResponseEntity.ok().build();
    }
}
```

2. **Configuración del webhook en Supabase:**

```sql
-- supabase/migrations/00003_configure_webhook.sql
-- Supabase Database Webhooks se configuran via Dashboard o supabase/config.toml
-- Trigger: INSERT en auth.users
-- URL: http://api-gateway:8080/webhooks/supabase/user-registered
-- Headers: X-Supabase-Webhook-Secret: ${SUPABASE_WEBHOOK_SECRET}
```

```toml
# supabase/config.toml (sección de webhooks)
[db.webhooks.user_registered]
enabled = true
url = "http://api-gateway:8080/webhooks/supabase/user-registered"
events = ["INSERT"]
table = "auth.users"
```

#### Mapeo UUID/BIGINT: Supabase Auth ↔ MS-Users

Supabase Auth usa UUID para identificar usuarios (`auth.users.id`), mientras que MS-Users usa BIGINT auto-increment (`PLAYER.id`). Para mantener compatibilidad:

**Migración SQL en MS-Users:**

```sql
-- Migración en user_db
ALTER TABLE player ADD COLUMN supabase_user_id UUID;
CREATE UNIQUE INDEX idx_player_supabase_user_id ON player(supabase_user_id);
```

**Lógica de mapeo:**

```java
// MS-Users: PlayerRepository
@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {
    Optional<Player> findBySupabaseUserId(UUID supabaseUserId);
    Optional<Player> findByEmail(String email);
}

// MS-Users: al recibir evento user.registered
@RabbitListener(queues = "user.registered.queue")
public void handleUserRegistered(UserRegisteredEvent event) {
    Player player = new Player();
    player.setSupabaseUserId(UUID.fromString(event.getPayload().getUserId()));
    player.setEmail(event.getPayload().getEmail());
    player.setFirstName(event.getPayload().getFirstName());
    player.setLastName(event.getPayload().getLastName());
    playerRepository.save(player); // BIGINT id auto-generado
}
```

**Flujo de resolución de identidad:**
```
1. Frontend → Supabase Auth → JWT con sub=UUID
2. API Gateway → extrae UUID → propaga X-User-Id=UUID
3. BFF-Player → recibe X-User-Id (UUID)
4. BFF-Player → llama MS-Users: GET /users/by-supabase-id/{uuid}
5. MS-Users → busca PLAYER por supabase_user_id → retorna PLAYER con id BIGINT
6. BFF-Player → usa PLAYER.id (BIGINT) para operaciones internas
```

**Endpoint nuevo en MS-Users:**
```java
@GetMapping("/users/by-supabase-id/{supabaseUserId}")
public ResponseEntity<PlayerProfileDTO> getBySupabaseId(
        @PathVariable UUID supabaseUserId) {
    return playerService.findBySupabaseUserId(supabaseUserId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
}
```

#### Integración Frontend

```typescript
// lib/supabase.ts (chess-portal y organizer-panel)
import { createClient } from '@supabase/supabase-js';

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;
const supabaseAnonKey = import.meta.env.VITE_SUPABASE_ANON_KEY;

export const supabase = createClient(supabaseUrl, supabaseAnonKey);
```

```typescript
// Registro
const { data, error } = await supabase.auth.signUp({
  email,
  password,
  options: {
    data: { role: 'PLAYER', firstName: 'Cristóbal', lastName: 'Henríquez' }
  }
});

// Login
const { data, error } = await supabase.auth.signInWithPassword({
  email,
  password
});

// Obtener token para API calls
const { data: { session } } = await supabase.auth.getSession();
const accessToken = session?.access_token;

// API calls con token
fetch('/api/player/me/dashboard', {
  headers: { Authorization: `Bearer ${accessToken}` }
});

// Auto-refresh y cambios de estado
supabase.auth.onAuthStateChange((event, session) => {
  if (event === 'SIGNED_OUT') {
    router.push('/login');
  }
});
```

---

### Fase 3: PGN Storage con Supabase Storage

**Validates: Requirements 6, 7, 12**

#### Cambios en MS-Game

MS-Game actualmente usa MinIO Client para subir/descargar PGN. Se reemplaza con Supabase Storage API via HTTP:

**Antes (MinIO):**
```java
MinioClient minioClient;
minioClient.putObject(PutObjectArgs.builder()
    .bucket("chessquery-pgn")
    .object("games/2026/04/4521.pgn")
    .stream(pgnStream, pgnSize, -1)
    .contentType("application/x-chess-pgn")
    .build());
```

**Después (Supabase Storage):**
```java
@Service
public class SupabaseStorageService implements StorageService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-key}")
    private String serviceKey;

    private final RestTemplate restTemplate;

    public String uploadPgn(String key, byte[] pgnContent) {
        String url = supabaseUrl + "/storage/v1/object/chessquery-pgn/" + key;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + serviceKey);
        headers.set("Content-Type", "application/x-chess-pgn");

        HttpEntity<byte[]> entity = new HttpEntity<>(pgnContent, headers);
        restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        return key; // almacenar en GAME.pgn_storage_key
    }

    public String generatePresignedUrl(String key) {
        String url = supabaseUrl + "/storage/v1/object/sign/chessquery-pgn/" + key;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + serviceKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("expiresIn", 3600); // 1 hora
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
            url, HttpMethod.POST, entity, Map.class);

        String signedUrl = (String) response.getBody().get("signedURL");
        return supabaseUrl + "/storage/v1" + signedUrl;
    }
}
```

#### Interfaz StorageService

Para facilitar la migración y permitir rollback, se introduce una interfaz:

```java
public interface StorageService {
    String uploadPgn(String key, byte[] pgnContent);
    String generatePresignedUrl(String key);
}
```

Con implementaciones `MinioStorageService` (actual) y `SupabaseStorageService` (nueva). La implementación activa se selecciona por configuración:

```yaml
# application.yml
storage:
  provider: supabase  # o "minio" para rollback
```

---

### Fase 4: Descomisionamiento

**Validates: Requirements 11, 12, 10**

#### Orden de descomisionamiento

1. **Verificar** que todos los tests E2E pasan sin MS-Auth ni MinIO
2. **Archivar** MS-Auth en branch `backup/ms-auth-pre-supabase`
3. **Crear** `docker-compose.backup.yml` con configuración de MinIO
4. **Remover** servicios de docker-compose.yml: `ms-auth`, `auth_db`, `minio`
5. **Remover** dependencias de MS-Auth en API Gateway (routes, WebClient config)
6. **Remover** dependencias de MinIO en MS-Game (pom.xml, MinioClient, MinioConfig)
7. **Actualizar** documentación: CONTEXT.md, README.md, PLAN_DEMO.md
8. **Verificar** que el sistema funciona con 8 componentes

#### Compatibilidad backward

Los microservicios custom requieren cambios mínimos:
- `X-User-Id` ahora propaga UUID de Supabase (antes era BIGINT de MS-Auth). Los BFFs resuelven a BIGINT via `GET /users/by-supabase-id/{uuid}`
- `X-User-Email`, `X-User-Role` se propagan con el mismo formato
- Los eventos RabbitMQ mantienen su estructura de payload; el publisher cambia de MS-Auth a webhook de Supabase en API Gateway (implementado en Fase 2)
- `GAME.pgn_storage_key` mantiene el mismo formato de key (`games/{year}/{month}/{gameId}.pgn`)
- MS-Users agrega columna `supabase_user_id UUID` para mapeo de identidad (implementado en Fase 2)

---

## Testing Strategy

### Validation Approach

La estrategia de testing cubre 4 niveles:

1. **Unit Tests**: Validación de JWT parsing, construcción de presigned URLs, extracción de claims
2. **Integration Tests**: Flujos completos contra Supabase Local (register → login → API call → upload PGN → download)
3. **Property-Based Tests**: Validación de JWT con inputs variados, construcción de rutas de storage
4. **Smoke Tests**: Verificación E2E del flujo completo de demo

### Test Cases

#### JWT Validation (Gateway)
- JWT válido con claims correctos → extrae X-User-Id, X-User-Email, X-User-Role
- JWT expirado → retorna 401
- JWT con firma inválida → retorna 401
- JWT sin claim de role → consulta user_profiles como fallback
- Request sin header Authorization → retorna 401

#### Storage (MS-Game)
- Upload PGN válido (<1MB) → almacena y retorna key
- Upload PGN >1MB → retorna 413
- Generate presigned URL → URL válida con expiración 1 hora
- Download via presigned URL → archivo accesible
- Download con URL expirada → retorna 403

#### Auth Flow (E2E)
- Register → login → obtener dashboard → crear torneo → jugar partida → subir PGN
- Register con email duplicado → error 409
- Login con credenciales inválidas → error 401
- Refresh token → nuevo access token válido
- Logout → sesión revocada

### Rollback Testing

Antes de descomisionar, verificar que el sistema puede operar en ambos modos:
1. Con Supabase Auth + Supabase Storage (nuevo)
2. Con MS-Auth + MinIO (fallback) cambiando solo variables de entorno

---

## Security Considerations

### Secrets Management
- `SUPABASE_JWT_SECRET`: Solo en API Gateway (.env, nunca en código)
- `SUPABASE_SERVICE_KEY`: Solo en backends (MS-Game). NUNCA exponer a frontends
- `SUPABASE_ANON_KEY`: Seguro para frontends, limitado por RLS

### RLS Policies
- `user_profiles`: Solo lectura del propio perfil (auth.uid() = id)
- `storage.objects`: Upload solo por autenticados, lectura via service_role o presigned URL

### Rate Limiting
- Supabase Auth: Built-in rate limiting (configurable)
- API Gateway: Mantener rate limiting existente (100 req/min autenticados, 20 req/min auth endpoints)

---

## Environment Variables

Todas las variables de entorno requeridas por servicio:

### API Gateway
| Variable | Descripción | Ejemplo (Supabase Local) |
|---|---|---|
| `SUPABASE_URL` | URL base de Supabase API | `http://localhost:54321` |
| `SUPABASE_JWT_SECRET` | Secret para validar firma de JWT | `super-secret-jwt-token-with-at-least-32-characters-long` |
| `SUPABASE_WEBHOOK_SECRET` | Secret para validar webhooks entrantes | `webhook-secret-min-32-chars` |

### MS-Game
| Variable | Descripción | Ejemplo (Supabase Local) |
|---|---|---|
| `SUPABASE_URL` | URL base de Supabase API | `http://localhost:54321` |
| `SUPABASE_SERVICE_KEY` | Service key con privilegios admin | `eyJ...` (generado por `supabase start`) |
| `STORAGE_PROVIDER` | Provider activo: `supabase` o `minio` | `supabase` |

### MS-Users
| Variable | Descripción | Ejemplo |
|---|---|---|
| `SPRING_DATASOURCE_URL` | URL de user_db (sin cambios) | `jdbc:postgresql://postgres:5432/user_db` |

### chess-portal
| Variable | Descripción | Ejemplo (Supabase Local) |
|---|---|---|
| `VITE_SUPABASE_URL` | URL base de Supabase (expuesta a frontend) | `http://localhost:54321` |
| `VITE_SUPABASE_ANON_KEY` | Anon key pública (limitada por RLS) | `eyJ...` (generado por `supabase start`) |

### organizer-panel
| Variable | Descripción | Ejemplo (Supabase Local) |
|---|---|---|
| `VITE_SUPABASE_URL` | URL base de Supabase (expuesta a frontend) | `http://localhost:54321` |
| `VITE_SUPABASE_ANON_KEY` | Anon key pública (limitada por RLS) | `eyJ...` (generado por `supabase start`) |

### docker-compose.yml (variables compartidas)
| Variable | Descripción | Origen |
|---|---|---|
| `SUPABASE_URL` | URL de Supabase API | `.env` |
| `SUPABASE_JWT_SECRET` | JWT secret compartido | `.env` (nunca en código) |
| `SUPABASE_ANON_KEY` | Anon key pública | Generado por `supabase start` |
| `SUPABASE_SERVICE_KEY` | Service key admin | Generado por `supabase start` |
| `SUPABASE_WEBHOOK_SECRET` | Secret para webhooks | `.env` (generado manualmente) |

### .env.example (template para nuevos desarrolladores)
```env
# Supabase Configuration
SUPABASE_URL=http://localhost:54321
SUPABASE_JWT_SECRET=super-secret-jwt-token-with-at-least-32-characters-long
SUPABASE_ANON_KEY=your-anon-key-from-supabase-start
SUPABASE_SERVICE_KEY=your-service-key-from-supabase-start
SUPABASE_WEBHOOK_SECRET=your-webhook-secret-min-32-chars

# Frontend (prefijo VITE_ requerido por Vite)
VITE_SUPABASE_URL=http://localhost:54321
VITE_SUPABASE_ANON_KEY=your-anon-key-from-supabase-start

# Storage provider (supabase | minio)
STORAGE_PROVIDER=supabase
```

> **⚠️ Seguridad:** `SUPABASE_SERVICE_KEY` y `SUPABASE_JWT_SECRET` son secretos sensibles. Solo deben existir en `.env` (que está en `.gitignore`) y en secrets de CI/CD. NUNCA exponer a frontends ni commitear a git.

---

## Migration Risks and Mitigations

| Riesgo | Probabilidad | Impacto | Mitigación |
|---|---|---|---|
| JWT claims de Supabase difieren del formato esperado | Media | Alto | Escribir adapter en Gateway que normalice claims |
| Latencia de Supabase Storage mayor que MinIO local | Baja | Media | Benchmark antes de migrar; Supabase Local es rápido |
| Evento user.registered no se emite con Supabase | Baja | Alto | Mitigado: webhook POST /webhooks/supabase/user-registered implementado en Fase 2 |
| Rollback requerido después de descomisionar | Baja | Alto | Branch backup + docker-compose.backup.yml + docs/ROLLBACK.md |
| Conflictos de UUID entre Supabase Auth y MS-Users PLAYER.id | Baja | Alto | Mitigado: columna supabase_user_id UUID en PLAYER + endpoint de resolución |

---

## Affected Components Summary

| Componente | Cambio | Detalle |
|---|---|---|
| API Gateway | **Modificar** | JWT validation local en lugar de HTTP call a MS-Auth |
| MS-Game | **Modificar** | SupabaseStorageService reemplaza MinioStorageService |
| chess-portal | **Modificar** | Supabase Client para auth (signUp, signIn, signOut) |
| organizer-panel | **Modificar** | Supabase Client para auth |
| MS-Auth | **Eliminar** | Archivado en branch backup |
| MinIO | **Eliminar** | Configuración preservada en docker-compose.backup.yml |
| auth_db | **Eliminar** | Reemplazada por Supabase Auth DB (gestionada) |
| Supabase | **Nuevo** | Auth, Storage, user_profiles, migraciones SQL |
| docker-compose.yml | **Modificar** | Remover ms-auth, minio; agregar vars de Supabase |
| CONTEXT.md | **Modificar** | Actualizar arquitectura y contratos |
| .env.example | **Modificar** | Agregar variables de Supabase |

---

## Dependencies

- **Supabase CLI** >= 1.200.0 (para `supabase start/stop`)
- **@supabase/supabase-js** >= 2.x (frontend)
- **io.jsonwebtoken:jjwt-api** >= 0.11.5 (Gateway JWT validation)
- **Docker** >= 24.0 (para contenedores Supabase Local)
