# Plan de migración a Cloud (Supabase + AWS)

Estado actual: stack 100% local (Supabase CLI + Docker Compose en una máquina).
Objetivo: ChessQuery accesible públicamente con datos persistentes, dominios HTTPS y CI/CD.

La migración es en **dos ejes ortogonales** que se pueden hacer en cualquier orden:

1. **Supabase local → Supabase Cloud** (auth, base de datos lógica de identidad, storage de PGN, realtime).
2. **Docker Compose local → AWS** (microservicios Java, BFFs Node, gateway, RabbitMQ, Redis, postgres por servicio, frontends estáticos).

Recomendación: **hacer 1 antes que 2**. Supabase Cloud no requiere infra propia y desbloquea pruebas con usuarios reales antes de pagar nada de AWS.

---

## Eje 1 — Supabase Cloud

### 1.1 Linkear el proyecto

Ya tenés un proyecto creado (`agusnoopy3000's Project`, plan FREE). Conseguir el ref:
- Dashboard → Settings → General → Reference ID

```bash
supabase login                          # solo la primera vez
supabase link --project-ref <ref>       # ejecutado desde la raíz del repo
```

Esto crea `.supabase/` con metadata local pero NO sube nada todavía.

### 1.2 Subir las migraciones

Toda la estructura de `supabase/migrations/` se aplica al proyecto cloud:

```bash
supabase db push                        # idempotente, aplica solo lo nuevo
```

Verificar en Studio cloud (Database → Tables) que están: `user_profiles`, las RLS policies, el bucket `chessquery-pgn` y sus policies.

> Si `db push` falla por checksum mismatch, NO usar `--include-all` ciegamente. Comparar `supabase migration list` local vs remoto y resolver manualmente.

### 1.3 Configurar el bucket de PGN en cloud

El bucket se crea por migración SQL pero las RLS policies de Storage a veces requieren ajuste manual. Verificar en Studio → Storage → `chessquery-pgn`:
- Bucket privado (no public).
- Policy de lectura: solo el dueño del game O service_role.
- Policy de escritura: solo service_role (MS-Game usa service key).

### 1.4 Service role key y JWT secret

En Cloud Dashboard → Settings → API:
- **Project URL** → reemplaza `SUPABASE_URL` (será `https://<ref>.supabase.co`).
- **anon public key** → `SUPABASE_ANON_KEY` y `VITE_SUPABASE_ANON_KEY`.
- **service_role key** → `SUPABASE_SERVICE_KEY` (solo backend, nunca al front).
- **JWT Settings → JWT Secret** → `SUPABASE_JWT_SECRET`.

> El JWT secret cloud es distinto al local. Si rotás keys, hay que reiniciar todos los servicios Java porque el filtro JWT cachea la JWKS.

### 1.5 Webhook user.registered

En cloud, Database → Webhooks → Create:
- Event: `INSERT` on `auth.users`.
- HTTP method: `POST`.
- URL: el endpoint público de tu API Gateway (sección AWS abajo). Mientras desarrollás, podés usar `ngrok http 8080` y poner la URL de ngrok temporal.
- Header `X-Webhook-Secret`: el valor de `SUPABASE_WEBHOOK_SECRET`.

Sin este paso, los registros en cloud no crean `Player` en MS-Users.

### 1.6 Auth → URL Configuration

En cloud, Authentication → URL Configuration:
- **Site URL**: dominio público del chess-portal (ej. `https://app.chessquery.cl`).
- **Redirect URLs**: agregar el de localhost para dev y los dominios de prod.

Sin esto, los magic links del invitador van a redirect a la URL por defecto de Supabase y se rompe el flujo de "click → entrar a la partida".

### 1.7 Email templates

Authentication → Email Templates → editar al menos el de "Magic Link" para que diga "Te invitan a jugar una partida en ChessQuery" en vez del genérico.

> En FREE tier, Supabase manda los emails desde sus servidores (rate-limited, marca de "via supabase.io"). Para algo más serio, conectar SMTP propio en Settings → Auth → SMTP (SendGrid free, Resend, AWS SES).

### 1.8 Switch del .env

```ini
# infrastructure/.env (backend)
SUPABASE_URL=https://<ref>.supabase.co
SUPABASE_PUBLIC_URL=https://<ref>.supabase.co
SUPABASE_JWT_SECRET=<el de cloud>
SUPABASE_ANON_KEY=<anon de cloud>
SUPABASE_SERVICE_KEY=<service_role de cloud>
SUPABASE_WEBHOOK_SECRET=<el que pusiste en webhook config>

# frontend/apps/*/.env
VITE_SUPABASE_URL=https://<ref>.supabase.co
VITE_SUPABASE_ANON_KEY=<anon de cloud>
```

### 1.9 Cuotas FREE a tener en cuenta

- 50 000 MAU.
- 500 MB DB (ChessQuery cabe sobrado para demo).
- 1 GB Storage (cada PGN <5 KB → ~200 000 partidas).
- 2 GB egress / mes.
- **Pausa automática del proyecto si está 7 días sin actividad** — la primera request después tarda ~30 s en revivir. Si vas a demoar después de inactividad, abrí el dashboard 5 min antes.

---

## Eje 2 — Microservicios y BFFs en AWS

El stack de Docker Compose tiene **20 contenedores**. Tirarlos uno por uno como instancias EC2 sale carísimo y no aporta. Hay tres caminos posibles, ordenados por costo y complejidad:

### Opción A — Una EC2 con Docker Compose tal cual (más barato, recomendado para demo)

Una instancia `t3.medium` (4 GB RAM) o `t3.large` (8 GB) corriendo el mismo `docker compose` que usás local.

**Pasos**:
1. Crear EC2 Ubuntu 22.04, security group abriendo 22 (tu IP) y 80/443 (todo el mundo).
2. Asignar Elastic IP (gratis si está conectada) y apuntar dominio (Route53 o tu registrador).
3. Instalar Docker + Compose.
4. Build y publicar imágenes a Docker Hub (o ECR público) desde tu máquina local con `make build` (a agregar al Makefile, ítem 11 de la lista pendiente).
5. `git clone` el repo en la EC2, copiar `.env` con valores de prod (Supabase Cloud + secretos rotados).
6. `make up`.
7. Nginx del compose ya escucha en 80 → agregar **certbot/letsencrypt** delante para HTTPS, o reemplazar el nginx del compose por **Caddy** que hace TLS automático.

**Costo**: t3.medium ~$30/mes + storage EBS ~$5/mes + transferencia. ~$40/mes total.

**Limitaciones**: una sola máquina, sin alta disponibilidad, los Postgres por servicio son volúmenes locales (backup manual con `pg_dump` + cron a S3).

### Opción B — Servicios desacoplados (intermedio, ~$80–150/mes)

Microservicios Java en **ECS Fargate** o EC2 + ALB. Postgres por servicio en **RDS** (un único servidor con varias databases lógicas en vez de 6 instancias separadas — el modelo "una DB por servicio" se mantiene, solo cambia el server). RabbitMQ en **Amazon MQ** o un EC2 dedicado. Redis en **ElastiCache**. Frontends en **S3 + CloudFront**.

**Cuándo migrar a esto**: cuando tengas tráfico real (>100 usuarios concurrentes), o cuando el cliente exija HA. Para demo académica, A es suficiente.

### Opción C — Kubernetes en EKS

Para el alcance del proyecto académico es overkill y cara. Solo mencionar como dirección futura si el equipo quiere meter k8s en el currículum.

### 2.1 Frontends estáticos (chess-portal, organizer-panel)

Independiente de qué opción elijas para backend, los frontends son archivos estáticos. Lo más barato:

```bash
cd frontend/apps/chess-portal && npm run build   # → dist/
aws s3 sync dist/ s3://chessquery-chess-portal/ --delete
aws cloudfront create-invalidation --distribution-id <id> --paths "/*"
```

CloudFront free tier: 1 TB de transferencia / mes el primer año.

### 2.2 CI/CD mínimo

`.github/workflows/` no existe hoy. Una sola pipeline:

```yaml
# .github/workflows/deploy.yml
name: Build & deploy
on:
  push:
    branches: [main]
jobs:
  test:
    # mvn test en ms-* y npm test en frontend/*
  build-images:
    # docker buildx + push a Docker Hub o ECR
  deploy:
    # ssh a EC2 y `docker compose pull && docker compose up -d`
    # OR aws s3 sync dist/ + cloudfront invalidate
```

Detalles en una iteración posterior. Para la demo basta `make up` manual en la EC2.

### 2.3 Variables de entorno y secretos

No commitear `.env` con valores de cloud. Opciones:
- **AWS Secrets Manager** (caro, pago por secret).
- **AWS Systems Manager Parameter Store** (free tier 10k parameters, cifrado con KMS gratuito por usuario).
- **GitHub Actions secrets** + inyectar en el deploy.

Para demo, `scp` del `.env` la primera vez y dejarlo en la EC2 con `chmod 600` es válido.

---

## Eje 3 — Cosas que hay que cambiar en código antes de ir a cloud

Notas de implementación detectadas hoy que rompen en producción si no se ajustan:

### 3.1 `cloud.aws.s3.endpoint` hardcoded

Las properties `application.yml` de MS-Game asumen MinIO local. En cloud (Supabase Storage o AWS S3 directo) hay que sacar `path-style-access-enabled` y `endpoint`. Hoy ya está parcialmente migrado a Supabase Storage; verificar que `STORAGE_PROVIDER=supabase` esté seteado y MinIO esté completamente fuera (commit pendiente: limpiar `.env.example` que aún tiene `S3_*` y `MINIO_*`).

### 3.2 host.docker.internal

`SUPABASE_URL=http://host.docker.internal:54321` solo funciona local. En cloud apunta a la URL pública (`https://<ref>.supabase.co`). Reemplazar tras cambiar `.env`.

### 3.3 X-User-Id propagation bug (P0, bloquea flujo de torneos)

Detectado en verificación E2E:
- [api-gateway/.../SupabaseJwtAuthFilter.java:221](../api-gateway/src/main/java/cl/chessquery/gateway/filter/SupabaseJwtAuthFilter.java) inyecta `X-User-Id` con la **UUID de Supabase** (`claims.getSubject()`).
- MS-Tournament lo bindea a `Long` (player.id) y rompe con `400 INVALID_PARAMETER`.

**Fix antes de migrar**: el filtro debe llamar a `MS-Users /users/by-supabase-id/{uuid}` y propagar el `id` numérico. Cachear la resolución (Caffeine, TTL 5 min) para no agregar un hop por request.

### 3.4 Healthchecks de Java en compose

Los servicios Java aparecen "Up X minutes" sin discriminar healthy/unhealthy. Agregar:

```yaml
healthcheck:
  test: ["CMD", "wget", "-qO-", "http://localhost:8082/actuator/health"]
  interval: 15s
  timeout: 3s
  retries: 5
  start_period: 60s
```

Crítico para que los `depends_on: { condition: service_healthy }` funcionen y para que ECS/ALB detecten cuando un container está zombie.

### 3.5 SSO entre chess-portal y organizer-panel

Hoy un organizador que tiene también cuenta player tiene que loguearse 2 veces (puertos distintos = sesiones distintas en localStorage). Resolver con cookie de dominio padre cuando ambos vivan bajo `*.chessquery.cl` (resuelve solo en cloud), o pasar el access_token vía URL al cambiar de app.

---

## Cronograma sugerido

| Etapa | Trabajo | Esfuerzo |
|---|---|---|
| 1 | `supabase db push` + `.env` cloud + smoke E2E contra cloud | 2 h |
| 2 | Fix X-User-Id propagation + redeploy local + retest | 3 h |
| 3 | Healthchecks compose + Makefile `build` + .env.example limpio | 1 h |
| 4 | EC2 + dominio + Caddy/certbot + first deploy manual | 4 h |
| 5 | S3+CloudFront para frontends | 2 h |
| 6 | GitHub Actions deploy básico | 3 h |
| 7 | Webhook cloud apuntando a EC2 + test E2E completo | 1 h |

Total: ~16 h de trabajo enfocado para tener ChessQuery públicamente accesible con HTTPS, datos en cloud y deploy automatizado.

---

## Rollback

- Local sigue funcionando con `supabase start` + `make up`. La migración a cloud no rompe el flujo dev.
- `git revert` del commit que cambia `.env.example` con valores cloud.
- Si Supabase Cloud se corrompe, `supabase db pull` para traerse el schema y `pg_dump` para los datos críticos antes de cualquier `db reset`.
