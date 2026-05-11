# Plan de Migración a Supabase (Opción A - Reemplazo Parcial)

**Fecha:** 2026-05-01  
**Objetivo:** Reemplazar MS-Auth y MinIO con Supabase para simplificar la arquitectura de la demo, manteniendo microservicios custom para lógica de negocio compleja.

---

## 🎯 Alcance de la Migración

### ✅ Servicios a REEMPLAZAR con Supabase

| Servicio Actual | Reemplazo Supabase | Razón |
|-----------------|-------------------|-------|
| **MS-Auth** (Spring Boot) | Supabase Auth | Auth es commodity, Supabase lo hace mejor |
| **MinIO** (Object Storage) | Supabase Storage | Storage integrado, menos infraestructura |
| **PostgreSQL auth_db** | Supabase Auth DB | Gestionado por Supabase |

### ✅ Servicios a MANTENER (Lógica Custom)

| Servicio | Razón para Mantener |
|----------|---------------------|
| **MS-Users** | Modelo de datos custom (PLAYER, COUNTRY, CLUB, RATING_HISTORY) |
| **MS-Tournament** | Lógica compleja: Factory Method para pareos (SWISS, ROUND_ROBIN, KNOCKOUT) |
| **MS-Game** | Cálculo ELO, detección de aperturas, lógica de partidas |
| **MS-ETL** | Sincronización FIDE/AJEFECH, circuit breaker |
| **MS-Notifications** | Lógica de notificaciones por eventos RabbitMQ |
| **BFF-Player** | Agregación específica para vista de jugador |
| **BFF-Organizer** | Agregación específica para vista de organizador |
| **API Gateway** | Enrutamiento y rate limiting (se integra con Supabase Auth) |

### 📊 Resultado

**Antes:** 9 servicios backend + MinIO + PostgreSQL (11 componentes)  
**Después:** 7 servicios backend + Supabase (8 componentes)  
**Reducción:** 3 componentes menos (-27%)

---

## 🏗️ Arquitectura Resultante

```
┌─────────────────────────────────────────────────────────────┐
│                         FRONTEND                             │
├──────────────────────┬──────────────────────────────────────┤
│  Chess Portal        │  Organizer Panel                     │
│  (Player Workspace)  │  (Organizer Workspace)               │
└──────────┬───────────┴──────────────────┬───────────────────┘
           │                               │
           │         ┌─────────────────────┴──────────┐
           │         │                                 │
           ▼         ▼                                 ▼
    ┌──────────────────────────────────────────────────────┐
    │              API GATEWAY (Spring Cloud)              │
    │  - Routing                                           │
    │  - Rate Limiting (Redis)                             │
    │  - JWT Validation (Supabase Auth)  ◄─────────┐      │
    └──────────────────────────────────────────────┼──────┘
                       │                           │
                       │                           │
    ┌──────────────────┴───────────────┐          │
    │                                   │          │
    ▼                                   ▼          │
┌─────────────────┐            ┌─────────────────┐│
│  SUPABASE       │            │  MICROSERVICES  ││
│  ===============│            │  ==============  ││
│                 │            │                  ││
│  ✅ Auth        │            │  MS-Users        ││
│  - JWT          │            │  MS-Tournament   ││
│  - Refresh      │            │  MS-Game         ││
│  - Roles        │            │  MS-ETL          ││
│  - Sessions     │            │  MS-Notifications││
│                 │            │                  ││
│  ✅ Storage     │            │  BFF-Player      ││
│  - PGN files    │            │  BFF-Organizer   ││
│  - Presigned    │            │                  ││
│    URLs         │            └─────────┬────────┘│
│                 │                      │         │
│  ✅ PostgreSQL  │                      │         │
│  - Auth tables  │                      │         │
│  - User mgmt    │                      │         │
│                 │                      │         │
│  ✅ Realtime    │                      │         │
│  - Subscriptions│                      │         │
│                 │                      │         │
└─────────────────┘                      │         │
                                         │         │
                    ┌────────────────────┴─────────┴────┐
                    │                                    │
                    ▼                                    ▼
            ┌──────────────┐                  ┌──────────────┐
            │ PostgreSQL   │                  │  RabbitMQ    │
            │ (user_db,    │                  │  (Events)    │
            │  tournament_ │                  └──────────────┘
            │  db, game_db,│
            │  etl_db,     │
            │  notif_db)   │
            └──────────────┘
```

---

## 📅 Cronograma de Implementación (2 Semanas)

### **Semana 1: Setup y Migración de Auth**

#### **Día 1-2: Setup Supabase Local**
- [ ] Instalar Supabase CLI
  ```bash
  npm install -g supabase
  ```
- [ ] Inicializar proyecto Supabase
  ```bash
  cd infrastructure
  supabase init
  supabase start
  ```
- [ ] Configurar `docker-compose.yml` para incluir Supabase
- [ ] Verificar que Supabase levanta correctamente:
  - Studio UI: http://localhost:54323
  - API: http://localhost:54321
  - Auth: http://localhost:54321/auth/v1
  - Storage: http://localhost:54321/storage/v1

#### **Día 3-4: Migrar Auth de MS-Auth a Supabase**
- [ ] Crear schema de usuarios en Supabase
  ```sql
  -- Supabase ya tiene auth.users
  -- Agregar custom claims para roles
  CREATE TABLE public.user_profiles (
    id UUID REFERENCES auth.users PRIMARY KEY,
    role TEXT CHECK (role IN ('PLAYER', 'ORGANIZER', 'ADMIN')),
    created_at TIMESTAMPTZ DEFAULT NOW()
  );
  ```
- [ ] Configurar Supabase Auth policies (RLS)
- [ ] Crear endpoints de registro/login usando Supabase JS Client
- [ ] Migrar lógica de refresh tokens (Supabase lo maneja automáticamente)
- [ ] Actualizar API Gateway para validar JWT de Supabase
  ```java
  // JwtAuthFilter.java
  // Cambiar validación de MS-Auth a Supabase
  String supabaseJwtSecret = env.getProperty("SUPABASE_JWT_SECRET");
  // Validar JWT con secret de Supabase
  ```

#### **Día 5: Integrar Frontends con Supabase Auth**
- [ ] Instalar `@supabase/supabase-js` en chess-portal y organizer-panel
  ```bash
  npm install @supabase/supabase-js
  ```
- [ ] Crear cliente Supabase en frontends
  ```typescript
  import { createClient } from '@supabase/supabase-js'
  
  const supabase = createClient(
    process.env.VITE_SUPABASE_URL,
    process.env.VITE_SUPABASE_ANON_KEY
  )
  ```
- [ ] Reemplazar llamadas a `/auth/login` con `supabase.auth.signInWithPassword()`
- [ ] Reemplazar llamadas a `/auth/register` con `supabase.auth.signUp()`
- [ ] Implementar manejo automático de refresh tokens
- [ ] Actualizar guards de autenticación

#### **Día 6-7: Testing de Auth**
- [ ] Smoke tests de registro/login
- [ ] Verificar que JWT de Supabase funciona con API Gateway
- [ ] Verificar que roles (PLAYER/ORGANIZER/ADMIN) se propagan correctamente
- [ ] Testing de refresh tokens automático
- [ ] Testing de logout

### **Semana 2: Migración de Storage y Ajustes Finales**

#### **Día 8-9: Migrar MinIO a Supabase Storage**
- [ ] Crear bucket `chessquery-pgn` en Supabase Storage
  ```typescript
  // Via Supabase Studio o CLI
  supabase storage create-bucket chessquery-pgn --public
  ```
- [ ] Configurar políticas de acceso (RLS)
  ```sql
  -- Solo usuarios autenticados pueden subir
  CREATE POLICY "Authenticated users can upload PGN"
  ON storage.objects FOR INSERT
  TO authenticated
  WITH CHECK (bucket_id = 'chessquery-pgn');
  
  -- Todos pueden leer (presigned URLs)
  CREATE POLICY "Anyone can read PGN"
  ON storage.objects FOR SELECT
  TO public
  USING (bucket_id = 'chessquery-pgn');
  ```
- [ ] Actualizar MS-Game para usar Supabase Storage
  ```java
  // Reemplazar MinioClient con Supabase Storage API
  // POST /storage/v1/object/chessquery-pgn/{gameId}.pgn
  ```
- [ ] Actualizar generación de presigned URLs
  ```java
  // Supabase genera URLs firmadas automáticamente
  String url = supabaseClient.storage()
    .from("chessquery-pgn")
    .createSignedUrl(key, 3600); // 1 hora
  ```
- [ ] Migrar PGN files existentes de MinIO a Supabase (si aplica)

#### **Día 10: Actualizar BFFs**
- [ ] BFF-Player: Actualizar endpoints que consumen Auth
  - Cambiar validación de tokens
  - Actualizar headers (Supabase usa `Authorization: Bearer`)
- [ ] BFF-Organizer: Actualizar endpoints que consumen Auth
- [ ] Verificar que agregaciones siguen funcionando

#### **Día 11: Actualizar Docker Compose**
- [ ] Eliminar servicio `ms-auth` de `docker-compose.yml`
- [ ] Eliminar servicio `minio` de `docker-compose.yml`
- [ ] Agregar Supabase services (o usar Supabase CLI)
  ```yaml
  # Opción 1: Usar supabase start (recomendado)
  # Opción 2: Agregar servicios manualmente
  supabase-db:
    image: supabase/postgres:15.1.0.117
    # ... config
  
  supabase-auth:
    image: supabase/gotrue:v2.99.0
    # ... config
  
  supabase-storage:
    image: supabase/storage-api:v0.40.4
    # ... config
  ```
- [ ] Actualizar variables de entorno en todos los servicios
  ```env
  SUPABASE_URL=http://localhost:54321
  SUPABASE_ANON_KEY=eyJhbGc...
  SUPABASE_SERVICE_KEY=eyJhbGc...
  ```
- [ ] Actualizar healthchecks

#### **Día 12: Testing E2E**
- [ ] Smoke test completo del flujo de demo:
  - Registro de usuario (Supabase Auth)
  - Login (Supabase Auth)
  - Crear torneo (MS-Tournament)
  - Inscribirse a torneo (MS-Tournament)
  - Jugar partida (MS-Game)
  - Subir PGN (Supabase Storage)
  - Descargar PGN (Supabase Storage presigned URL)
- [ ] Verificar eventos RabbitMQ siguen funcionando
- [ ] Verificar notificaciones siguen funcionando
- [ ] Performance testing básico

#### **Día 13: Documentación y Cleanup**
- [ ] Actualizar `CONTEXT.md` con nueva arquitectura
- [ ] Actualizar `PLAN_DEMO.md` (ya lo harás con el bugfix)
- [ ] Crear `docs/SUPABASE_SETUP.md` con instrucciones
- [ ] Actualizar README.md con nuevos pasos de setup
- [ ] Eliminar código de MS-Auth (archivar en branch)
- [ ] Eliminar configuración de MinIO

#### **Día 14: Buffer y Ensayo**
- [ ] Resolver issues pendientes
- [ ] Ensayo completo de la demo
- [ ] Preparar talking points sobre la arquitectura híbrida

---

## 🔧 Configuración Técnica Detallada

### **1. Supabase Local Setup**

```bash
# Instalar CLI
npm install -g supabase

# Inicializar proyecto
cd infrastructure
supabase init

# Levantar stack completo
supabase start

# Output esperado:
# API URL: http://localhost:54321
# GraphQL URL: http://localhost:54321/graphql/v1
# DB URL: postgresql://postgres:postgres@localhost:54322/postgres
# Studio URL: http://localhost:54323
# Inbucket URL: http://localhost:54324
# JWT secret: super-secret-jwt-token-with-at-least-32-characters-long
# anon key: eyJhbGc...
# service_role key: eyJhbGc...
```

### **2. Integración API Gateway con Supabase Auth**

```java
// api-gateway/src/main/java/cl/chessquery/gateway/filter/JwtAuthFilter.java

@Component
public class JwtAuthFilter implements GatewayFilter {
    
    @Value("${supabase.jwt.secret}")
    private String supabaseJwtSecret;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String token = extractToken(exchange.getRequest());
        
        if (token == null) {
            return unauthorized(exchange);
        }
        
        try {
            // Validar JWT de Supabase
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(supabaseJwtSecret.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
            
            // Extraer datos del usuario
            String userId = claims.getSubject(); // UUID de Supabase
            String email = claims.get("email", String.class);
            String role = claims.get("user_metadata", Map.class).get("role", String.class);
            
            // Agregar headers downstream
            ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", userId)
                .header("X-User-Email", email)
                .header("X-User-Role", role)
                .build();
            
            return chain.filter(exchange.mutate().request(modifiedRequest).build());
            
        } catch (JwtException e) {
            return unauthorized(exchange);
        }
    }
}
```

### **3. Frontend Integration**

```typescript
// frontend/apps/chess-portal/src/lib/supabase.ts

import { createClient } from '@supabase/supabase-js'

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL
const supabaseAnonKey = import.meta.env.VITE_SUPABASE_ANON_KEY

export const supabase = createClient(supabaseUrl, supabaseAnonKey, {
  auth: {
    autoRefreshToken: true,
    persistSession: true,
    detectSessionInUrl: true
  }
})

// Registro
export async function signUp(email: string, password: string, role: string) {
  const { data, error } = await supabase.auth.signUp({
    email,
    password,
    options: {
      data: {
        role: role // PLAYER, ORGANIZER, ADMIN
      }
    }
  })
  
  if (error) throw error
  return data
}

// Login
export async function signIn(email: string, password: string) {
  const { data, error } = await supabase.auth.signInWithPassword({
    email,
    password
  })
  
  if (error) throw error
  return data
}

// Logout
export async function signOut() {
  const { error } = await supabase.auth.signOut()
  if (error) throw error
}

// Get session
export async function getSession() {
  const { data: { session } } = await supabase.auth.getSession()
  return session
}

// Listen to auth changes
export function onAuthStateChange(callback: (session: Session | null) => void) {
  return supabase.auth.onAuthStateChange((_event, session) => {
    callback(session)
  })
}
```

### **4. MS-Game Integration con Supabase Storage**

```java
// ms-game/src/main/java/cl/chessquery/game/service/StorageService.java

@Service
public class SupabaseStorageService {
    
    @Value("${supabase.url}")
    private String supabaseUrl;
    
    @Value("${supabase.service.key}")
    private String serviceKey;
    
    private final RestTemplate restTemplate;
    
    public String uploadPgn(Long gameId, String pgnContent) {
        String key = String.format("games/%d/%d/%d.pgn",
            LocalDate.now().getYear(),
            LocalDate.now().getMonthValue(),
            gameId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + serviceKey);
        headers.setContentType(MediaType.valueOf("application/x-chess-pgn"));
        
        HttpEntity<String> request = new HttpEntity<>(pgnContent, headers);
        
        String url = supabaseUrl + "/storage/v1/object/chessquery-pgn/" + key;
        restTemplate.postForEntity(url, request, Void.class);
        
        return key;
    }
    
    public String getPresignedUrl(String key) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + serviceKey);
        
        String url = supabaseUrl + "/storage/v1/object/sign/chessquery-pgn/" + key;
        
        Map<String, Object> body = Map.of("expiresIn", 3600); // 1 hora
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
        String signedUrl = (String) response.getBody().get("signedURL");
        
        return supabaseUrl + signedUrl;
    }
}
```

---

## 🚀 CI/CD con GitHub Actions y AWS

### **Respuesta a tu pregunta: ¿Afectará el CI/CD?**

**Respuesta corta: NO afectará negativamente. De hecho, lo simplificará.**

### **Escenarios de Deployment**

#### **Opción 1: Supabase Cloud (Recomendado para Producción)** ⭐

```yaml
# .github/workflows/deploy-production.yml

name: Deploy to AWS

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      # Build microservices
      - name: Build Java services
        run: |
          cd ms-users && mvn clean package
          cd ms-tournament && mvn clean package
          cd ms-game && mvn clean package
          # ... otros servicios
      
      # Build frontends
      - name: Build frontends
        env:
          VITE_SUPABASE_URL: ${{ secrets.SUPABASE_URL }}
          VITE_SUPABASE_ANON_KEY: ${{ secrets.SUPABASE_ANON_KEY }}
        run: |
          cd frontend/apps/chess-portal && npm run build
          cd frontend/apps/organizer-panel && npm run build
      
      # Deploy to AWS
      - name: Deploy to ECS
        uses: aws-actions/amazon-ecs-deploy-task-definition@v1
        with:
          task-definition: task-definition.json
          service: chessquery-service
          cluster: chessquery-cluster
      
      # Deploy frontends to S3 + CloudFront
      - name: Deploy to S3
        run: |
          aws s3 sync frontend/apps/chess-portal/dist s3://chessquery-portal
          aws cloudfront create-invalidation --distribution-id ${{ secrets.CF_DIST_ID }}
```

**Ventajas:**
- ✅ Supabase Cloud maneja Auth, Storage, DB automáticamente
- ✅ Solo despliegas tus microservicios custom a AWS
- ✅ Menos infraestructura que gestionar en AWS
- ✅ Supabase tiene CDN global incluido
- ✅ Backups automáticos

**Arquitectura en Producción:**
```
AWS:
├── ECS/Fargate: MS-Users, MS-Tournament, MS-Game, MS-ETL, MS-Notifications
├── ECS/Fargate: BFF-Player, BFF-Organizer, API Gateway
├── RDS PostgreSQL: user_db, tournament_db, game_db, etl_db, notif_db
├── ElastiCache Redis: Rate limiting
├── Amazon MQ (RabbitMQ): Event bus
├── S3 + CloudFront: Frontends
└── ALB: Load balancer

Supabase Cloud:
├── Auth (global)
├── Storage (global CDN)
└── PostgreSQL (managed)
```

**Costos estimados:**
- Supabase: $25/mes (Pro plan) - incluye 8GB DB, 100GB storage, 50GB bandwidth
- AWS: ~$200-300/mes (ECS, RDS, ElastiCache, S3, CloudFront)
- **Total: ~$325/mes** (vs ~$400/mes sin Supabase por gestionar todo en AWS)

---

#### **Opción 2: Supabase Self-Hosted en AWS** (Más complejo)

```yaml
# .github/workflows/deploy-production.yml

name: Deploy to AWS (Self-hosted Supabase)

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      # Deploy Supabase stack to ECS
      - name: Deploy Supabase
        run: |
          # Supabase tiene Docker images oficiales
          # Desplegar: postgres, gotrue, storage-api, realtime, etc.
          aws ecs update-service --cluster chessquery --service supabase-auth
          aws ecs update-service --cluster chessquery --service supabase-storage
      
      # Deploy microservices
      - name: Deploy microservices
        run: |
          # ... mismo que Opción 1
```

**Ventajas:**
- ✅ Control total sobre la infraestructura
- ✅ Datos en tu VPC
- ✅ Sin vendor lock-in de Supabase Cloud

**Desventajas:**
- ⚠️ Más complejo de gestionar
- ⚠️ Tienes que manejar backups, updates, scaling
- ⚠️ Más costoso (más instancias ECS)

---

#### **Opción 3: Híbrido (Recomendado para Empezar)** ⭐⭐⭐

```
Demo/Dev:
└── Supabase Local (Docker)

Staging:
└── Supabase Cloud (Free tier)

Production:
└── Supabase Cloud (Pro plan)
```

**Pipeline CI/CD:**

```yaml
# .github/workflows/deploy.yml

name: CI/CD Pipeline

on:
  push:
    branches: [develop, staging, main]

env:
  AWS_REGION: us-east-1

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      # Setup Supabase local para tests
      - name: Setup Supabase
        run: |
          npm install -g supabase
          supabase start
      
      # Run tests
      - name: Run tests
        run: |
          # Tests de microservices
          cd ms-users && mvn test
          cd ms-tournament && mvn test
          # ... otros tests
  
  deploy-staging:
    needs: test
    if: github.ref == 'refs/heads/staging'
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to AWS Staging
        env:
          SUPABASE_URL: ${{ secrets.SUPABASE_STAGING_URL }}
          SUPABASE_ANON_KEY: ${{ secrets.SUPABASE_STAGING_ANON_KEY }}
        run: |
          # Deploy microservices to ECS staging
          # Frontends apuntan a Supabase Staging
  
  deploy-production:
    needs: test
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to AWS Production
        env:
          SUPABASE_URL: ${{ secrets.SUPABASE_PROD_URL }}
          SUPABASE_ANON_KEY: ${{ secrets.SUPABASE_PROD_ANON_KEY }}
        run: |
          # Deploy microservices to ECS production
          # Frontends apuntan a Supabase Production
```

---

## 📊 Comparación: Con vs Sin Supabase en AWS

| Aspecto | Sin Supabase (Todo AWS) | Con Supabase Cloud |
|---------|-------------------------|-------------------|
| **Servicios en ECS** | 9 microservices + Auth | 7 microservices (sin Auth) |
| **Gestión de Auth** | Custom (MS-Auth) | Supabase (managed) |
| **Storage** | S3 + Lambda presigned URLs | Supabase Storage (CDN incluido) |
| **DB para Auth** | RDS adicional | Incluido en Supabase |
| **Refresh Tokens** | Custom logic + Redis | Automático en Supabase |
| **Realtime** | Custom con WebSockets | Incluido en Supabase |
| **Backups Auth DB** | Manual (RDS snapshots) | Automático en Supabase |
| **CDN para Storage** | CloudFront adicional | Incluido en Supabase |
| **Costo mensual** | ~$400 | ~$325 |
| **Complejidad CI/CD** | Alta (más servicios) | Media (menos servicios) |
| **Time to market** | Más lento | Más rápido |

---

## ✅ Recomendación Final para CI/CD

**Para tu proyecto ChessQuery:**

1. **Demo (ahora):** Supabase Local (Docker)
2. **Staging:** Supabase Cloud Free tier
3. **Production:** Supabase Cloud Pro + AWS para microservices

**Ventajas para CI/CD:**
- ✅ **Menos servicios que desplegar** (7 vs 9)
- ✅ **Menos infraestructura que gestionar** (sin MS-Auth, sin MinIO)
- ✅ **Pipelines más simples** (menos pasos de build/deploy)
- ✅ **Rollbacks más fáciles** (Auth no cambia, solo microservices)
- ✅ **Secrets más simples** (solo SUPABASE_URL y SUPABASE_ANON_KEY)
- ✅ **Testing más rápido** (Supabase local levanta en segundos)

**No afecta negativamente:**
- ✅ Puedes seguir usando GitHub Actions
- ✅ Puedes seguir usando AWS ECS/Fargate
- ✅ Puedes seguir usando RDS para tus DBs custom
- ✅ De hecho, **simplifica** el pipeline

---

## 🎓 Talking Points para la Presentación

**"¿Por qué Supabase + Microservices Custom?"**

> "Usamos una arquitectura híbrida: Supabase para funcionalidades commodity como Auth y Storage, donde no tiene sentido reinventar la rueda. Pero mantuvimos microservices custom para nuestra lógica de negocio compleja:
> 
> - **MS-Tournament**: Implementa Factory Method para 3 algoritmos de pareo (Swiss, Round Robin, Knockout)
> - **MS-Game**: Calcula ELO con fórmula FIDE y detecta aperturas automáticamente
> - **MS-ETL**: Sincroniza con APIs externas (FIDE, AJEFECH) con circuit breaker
> 
> Esta decisión arquitectónica nos permitió:
> - Reducir 27% de componentes (de 11 a 8)
> - Enfocarnos en la lógica de negocio única de ChessQuery
> - Tener Auth enterprise-grade sin escribir código de seguridad
> - Desplegar más rápido con CI/CD simplificado"

---

## 📝 Próximos Pasos

1. **Ahora:** Implementar bugfix del PLAN_DEMO.md (tareas ya creadas)
2. **Día 1-2:** Setup Supabase local y familiarizarte con la API
3. **Día 3-7:** Migrar Auth (Semana 1)
4. **Día 8-12:** Migrar Storage (Semana 2)
5. **Día 13-14:** Testing y documentación

¿Quieres que te ayude a empezar con alguna de estas tareas específicas?
