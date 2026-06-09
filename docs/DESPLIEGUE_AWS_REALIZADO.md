# Despliegue ChessQuery en AWS — Qué se hizo (paso a paso)

> ℹ️ **¿Cuál despliegue es este?** Hay **dos despliegues independientes** (uno por cuenta):
> - **Este documento = despliegue ORIGINAL de _Agustín_** — cuenta `672782205900`,
>   ALB `chessquery-alb-984810293...`, buckets `chessquery-chess-portal` / `-organizer-panel`.
> - **Réplica de _Martin_** (cuenta propia `876204681432`) → ver
>   [`DESPLIEGUE_REPLICA_AWS.md`](DESPLIEGUE_REPLICA_AWS.md): ALB `chessquery-alb-453727502...`,
>   buckets `...-mmora`. Es un entorno **paralelo** con sus propios endpoints y su propio Supabase.
>
> La **arquitectura es idéntica** en ambos; solo cambian los endpoints/IDs por cuenta. Para
> operar la réplica de Martin usá siempre el doc de la réplica, no este.

> Documento de registro del despliegue real en AWS Academy (cuenta `672782205900`,
> región `us-east-1`). Cubre: **cluster ECS**, **manejo de credenciales**,
> **GitHub Actions** y **ECR**. Es el "cómo quedó" — para la guía operativa
> completa ver [`infrastructure/aws/RUNBOOK_ECS.md`](../infrastructure/aws/RUNBOOK_ECS.md).

---

## 0. Arquitectura final (resumen)

AWS Academy **bloquea Cloud Map / Service Discovery**, así que no hay DNS interno
entre microservicios. Por eso se eligió la **Opción A: una sola task ECS** con los
10 contenedores comunicándose por `localhost` (comparten network namespace en modo
`awsvpc`). Solo el `api-gateway:8080` queda público.

```
┌─ AWS (us-east-1, VPC default 172.31.0.0/16) ───────────────────────────┐
│                                                                         │
│  ECS Fargate cluster: chessquery-cluster                               │
│   └─ service chessquery-stack (1 task, 4 vCPU / 8 GB, IP pública)      │
│        ├─ rabbitmq  :5672   ├─ ms-users      :8081 ├─ bff-player  :3001 │
│        ├─ redis     :6379   ├─ ms-tournament :8082 ├─ bff-organizer:3002│
│        ├─ api-gateway :8080 ├─ ms-game       :8083                      │
│        │   (único público)  ├─ ms-analytics  :8084                      │
│        │                    └─ ms-notifications :8085                   │
│        └─ todos se hablan por http://localhost:<puerto>                 │
│                                                                         │
│  RDS PostgreSQL 16.8: chessquery-pg  (6 DBs: users/tournament/game/    │
│                                        analytics/notifications/etl)     │
│  ECR: 9 repos chessquery/<servicio>                                    │
│  Secrets Manager: db-password, rabbitmq-password, supabase-service-key,│
│                   jwt-secret, supabase-webhook-secret                   │
└─────────────────────────────────────────────────────────────────────────┘
        │ JWT (JWKS) + Storage
        ▼
   Supabase Cloud (proyecto pmtxxzscpactsgkijpul) — Auth + Storage + user_profiles
```

Componentes **fuera de la task**: `bff-admin` y `ms-etl` (fuera de alcance demo);
frontends (van a S3 — ver §5).

---

## 1. Cluster ECS Fargate

### 1.1 Creación
```bash
aws ecs create-cluster \
  --cluster-name chessquery-cluster \
  --capacity-providers FARGATE FARGATE_SPOT \
  --default-capacity-provider-strategy capacityProvider=FARGATE_SPOT,weight=4 capacityProvider=FARGATE,weight=1
```

### 1.2 Problema resuelto: service-linked role
En cuenta Academy nueva, el primer `create-cluster` falló con
*"Unable to assume the service linked role"*. Se resolvió creando (o confirmando)
el rol gestionado de ECS:
```bash
aws iam create-service-linked-role --aws-service-name ecs.amazonaws.com
# (si ya existe: AWSServiceRoleForECS — basta con reintentar create-cluster)
```

### 1.3 La task única
- **Task definition:** `infrastructure/aws/task-definitions/chessquery-stack.template.json`
  (plantilla con `${VARIABLES}` que se renderizan con `envsubst`).
- **Tamaño:** `cpu=4096` (4 vCPU), `memory=8192` (8 GB) — aloja 6 JVMs + 2 BFFs Node + RabbitMQ + Redis.
- **networkMode:** `awsvpc`, `assignPublicIp=ENABLED`.
- **Orden de arranque:** `dependsOn` con `condition: HEALTHY` — los MS esperan a
  RabbitMQ sano; el gateway espera a RabbitMQ y Redis.
- **Essential:** solo `rabbitmq`, `redis`, `api-gateway` (si caen, la task se reinicia);
  los MS/BFFs son `essential:false` para no tumbar todo si uno falla.

### 1.4 Service
```bash
# Security group: solo expone 8080 a internet
aws ec2 create-security-group --group-name chessquery-ecs-sg --vpc-id <VPC_ID>
aws ec2 authorize-security-group-ingress --group-id <SG> --protocol tcp --port 8080 --cidr 0.0.0.0/0

aws ecs create-service \
  --cluster chessquery-cluster --service-name chessquery-stack \
  --task-definition chessquery-stack --desired-count 1 --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[...],securityGroups=[<SG>],assignPublicIp=ENABLED}"
```
Script que lo automatiza: `scripts/create-ecs-service.sh`.

### 1.5 Problemas resueltos durante el arranque (importantes)
| Síntoma | Causa | Fix aplicado |
|---|---|---|
| `TaskFailedToStart`, RabbitMQ crashea | `.erlang.cookie: eacces` — quirk de la imagen rabbitmq en Fargate (el entrypoint deja el cookie inaccesible) | Wrapper en `command` que pre-siembra `/var/lib/rabbitmq/.erlang.cookie` legible (owner 100) antes de arrancar |
| `ms-users` crashea, resto OK | Profile `aws` usa `ddl-auto: validate`; la migración crea `gender CHAR(1)` pero la entidad espera `VARCHAR(1)` | Env `SPRING_JPA_HIBERNATE_DDL_AUTO=none` en los 5 MS (Flyway ya es dueño del esquema) |
| Gateway `UNHEALTHY` pese a arrancar OK | El healthcheck pegaba a `/actuator/health` (agregado) que daba DOWN por un indicador auxiliar | Healthcheck → `/actuator/health/readiness` (UP) |
| Indicador `supabaseAuth` DOWN | `SupabaseAuthHealthIndicator` llamaba a `/auth/v1/health` sin header `apikey` → 401 en Supabase Cloud | Código: ahora manda `apikey` y trata cualquier respuesta HTTP como reachable |

### 1.6 Encender / apagar (ahorro de saldo)
```bash
# Apagar (no borra nada)
aws ecs update-service --cluster chessquery-cluster --service chessquery-stack --desired-count 0
aws rds stop-db-instance --db-instance-identifier chessquery-pg
# Encender
aws ecs update-service --cluster chessquery-cluster --service chessquery-stack --desired-count 1
aws rds start-db-instance --db-instance-identifier chessquery-pg
```

### 1.7 Obtener la IP pública (cambia en cada reinicio de task)
```bash
TASK=$(aws ecs list-tasks --cluster chessquery-cluster --service-name chessquery-stack --query 'taskArns[0]' --output text)
ENI=$(aws ecs describe-tasks --cluster chessquery-cluster --tasks $TASK --query "tasks[0].attachments[0].details[?name=='networkInterfaceId'].value" --output text)
aws ec2 describe-network-interfaces --network-interface-ids $ENI --query 'NetworkInterfaces[0].Association.PublicIp' --output text
```
> ⚠️ Sin ALB/Elastic IP la IP cambia en cada redeploy. Para URL estable: ALB o EIP.

---

## 2. Manejo de credenciales

Tres "capas" de credenciales, cada una en su lugar correcto:

### 2.1 Credenciales AWS Academy (caducan cada 4 h)
- Se copian del lab ("AWS Details → AWS CLI") a `~/.aws/credentials` bajo `[default]`
  (access key + secret + **session token**, obligatorio en Academy).
- Región fija en `~/.aws/config` (`us-east-1`).
- **Cuando expiran:** se repegan al archivo local y se refrescan los GitHub Secrets
  (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`).
- Rol efectivo: `voclabs` (assumed-role de Academy). Para las tasks se reusa `LabRole`
  como `executionRoleArn` (Academy no deja crear roles IAM custom fácilmente).

### 2.2 Secretos de la aplicación → AWS Secrets Manager
Nunca van en texto plano en task-defs ni en git. Se guardan en Secrets Manager y la
task-def los inyecta por **ARN** (bloque `secrets:`), no por valor:

| Secret | Contenido |
|---|---|
| `chessquery/db-password` | password del master de RDS (generada con `openssl rand`) |
| `chessquery/rabbitmq-password` | password de RabbitMQ |
| `chessquery/supabase-service-key` | `service_role` key de Supabase (lo usa ms-game para Storage) |
| `chessquery/jwt-secret` | **Legacy JWT Secret** de Supabase (el gateway valida HS256 contra él) |
| `chessquery/supabase-webhook-secret` | secreto compartido del webhook `user.registered` |

ECS lee estos secretos en el arranque usando el `executionRole` (LabRole) y los
expone como variables de entorno dentro del contenedor.

### 2.3 Archivo de salidas local (gitignored)
`scripts/setup-aws.sh` escribe ARNs y endpoints (no los valores secretos) en
`infrastructure/aws/.deploy-outputs.env`, que está en `.gitignore`. Lo consumen
`set-gh-secrets.sh` y `create-ecs-service.sh`.

---

## 3. GitHub Actions (CI/CD)

### 3.1 Todo self-hosted (por billing)
La cuenta de GitHub tiene los **runners hosted bloqueados por un problema de
facturación** (los jobs en `ubuntu-latest` fallan con *"account is locked due to a
billing issue"*). Por eso **los 3 workflows corren en self-hosted**:

| Workflow | Qué hace | runs-on |
|---|---|---|
| `.github/workflows/ci.yml` | Tests (Java + BFF + frontend) en cada push/PR | `[self-hosted, Linux, X64]` |
| `.github/workflows/build-and-push.yml` | Build de 8 imágenes → ECR | `[self-hosted, Linux, X64]` |
| `.github/workflows/deploy.yml` | Render task-def + update-service ECS | `[self-hosted, Linux, X64]` |

> Pendiente: **registrar el runner self-hosted** (guía en `docs/SELF_HOSTED_RUNNER.md`).
> Mientras no haya runner registrado, los workflows quedan en cola. Por eso el primer
> despliegue se hizo **desde local** (ver §3.3).

### 3.2 GitHub Secrets configurados
14 secrets (cargados con `scripts/set-gh-secrets.sh`):
- Credenciales: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`, `AWS_REGION`, `AWS_ACCOUNT_ID`
- Infra/ARNs: `TASK_EXECUTION_ROLE_ARN`, `DB_HOST`, `RABBITMQ_HOST`, `SUPABASE_URL`,
  `DB_PASSWORD_ARN`, `RABBITMQ_PASSWORD_ARN`, `SUPABASE_SERVICE_KEY_ARN`,
  `JWT_SECRET_ARN`, `SUPABASE_WEBHOOK_SECRET_ARN`

### 3.3 Camino que se usó realmente (build local)
Como el runner self-hosted aún no estaba registrado, el primer build/deploy se hizo
desde la máquina local con scripts equivalentes a los workflows:
```bash
export SUPABASE_URL=... SUPABASE_SERVICE_KEY=... SUPABASE_JWT_SECRET=...
bash scripts/setup-aws.sh           # infra (ECR, cluster, RDS+DBs, Secrets)
bash scripts/set-gh-secrets.sh      # GitHub Secrets
export IMAGE_TAG=v0.1.0
bash scripts/build-push-ecr.sh      # build 8 imágenes → ECR (reemplaza build-and-push.yml)
bash scripts/create-ecs-service.sh  # registra task-def + crea el service
```
Una vez registrado el runner, el flujo automatizado es: push → `ci.yml` →
`gh workflow run build-and-push.yml` → `gh workflow run deploy.yml`.

---

## 4. ECR (registro de imágenes)

- **9 repositorios** `chessquery/<servicio>` (api-gateway, ms-users, ms-tournament,
  ms-game, ms-analytics, ms-notifications, ms-etl, bff-player, bff-organizer), creados
  con `scanOnPush=true` y `imageTagMutability=MUTABLE`.
- **No se usa Docker Hub** — las imágenes van **directo a ECR**. (Docker Hub solo
  aparece indirectamente como origen de las imágenes base `eclipse-temurin`,
  `rabbitmq`, `redis` al construir.)
- **Build & push** (Dockerfiles en `infrastructure/docker/<svc>/Dockerfile`, contexto
  = raíz del repo):
  ```bash
  aws ecr get-login-password | docker login --username AWS --password-stdin <ACCOUNT>.dkr.ecr.us-east-1.amazonaws.com
  docker build -f infrastructure/docker/api-gateway/Dockerfile -t <ECR>/chessquery/api-gateway:v0.1.0 .
  docker push <ECR>/chessquery/api-gateway:v0.1.0
  ```
- **Tags:** `v0.1.0` + `latest`. La task-def referencia `${ECR_REGISTRY}/chessquery/<svc>:${IMAGE_TAG}`.
- **Pull en ECS:** Fargate hace `pull` desde ECR usando el `executionRole` (LabRole con
  permisos de lectura de ECR) — tráfico interno de AWS, sin egress ni rate limits de Docker Hub.

---

## 5. Frontend en S3 — ¿sirve para esta infra?

**Sí, S3 (+ CloudFront) es la opción correcta** y encaja perfecto con esta arquitectura.
Los frontends (`chess-portal`, `organizer-panel`) son SPAs React/Vite → se compilan a
estáticos y se sirven desde S3; no necesitan ECS.

Por qué encaja:
- **Desacoplado del backend:** el SPA corre en el browser y le pega al `api-gateway`
  por su URL pública (`http://<IP>:8080` o, mejor, el DNS de un ALB).
- **Variables de build:** `VITE_SUPABASE_URL` y `VITE_SUPABASE_ANON_KEY` se compilan en
  el bundle (`.env.production`). El SPA habla con Supabase Cloud (auth) y con el gateway.
- **CORS:** el gateway ya expone `GATEWAY_CORS_ALLOWED_ORIGINS` (hoy `*`); en producción
  se acota a la URL del bucket/CloudFront.

Pasos (resumen):
```bash
cd frontend && npm run build
aws s3 mb s3://chessquery-chess-portal
aws s3 website s3://chessquery-chess-portal --index-document index.html --error-document index.html
aws s3 sync apps/chess-portal/dist/ s3://chessquery-chess-portal --delete
# (ideal) CloudFront delante para HTTPS + dominio
```

**Dos cosas a resolver antes:**
1. **URL estable del backend:** hoy la IP del gateway cambia en cada redeploy. El SPA
   necesita una URL fija → conviene poner un **ALB** delante del gateway (o una Elastic IP)
   y compilar el front contra ese DNS.
2. **Permisos S3 en Academy:** `s3:CreateBucket`/website hosting suelen estar permitidos;
   si CloudFront está restringido, el bucket como "static website" alcanza para la demo.

> En resumen: S3 sí, pero primero dale al gateway una URL estable (ALB) para no tener
> que recompilar el front cada vez que cambia la IP.

---

## 6. Estado al cierre

- ✅ Task `chessquery-stack` **RUNNING / HEALTHY** (10/10 contenedores)
- ✅ RDS + 6 DBs + Flyway aplicado
- ✅ 9 imágenes en ECR (`v0.1.0`)
- ✅ Secrets en Secrets Manager + 14 GitHub Secrets
- ⬜ Registrar runner self-hosted (para CI/CD automatizado)
- ✅ **Webhook Supabase → gateway** (2026-06-01, ver §6.3)
- ✅ **ALB para URL estable del gateway** (2026-06-01, ver §6.1)
- ✅ **Frontend a S3** (2026-06-01, ver §6.2)

### 6.1 ALB implementado (2026-06-01)
- **DNS estable:** `chessquery-alb-984810293.us-east-1.elb.amazonaws.com` (HTTP :80 → forward a `api-gateway:8080`).
- `SG_ALB=sg-071f18480ea23a5c4` (ingress 80 desde internet). `SG_ECS` (`sg-00d4ad8b2817ad4cc`) acepta 8080 desde el ALB.
- Target group `chessquery-gw-tg` tipo `ip`, health check `/actuator/health/readiness` → **healthy**.
- Service `chessquery-stack` **recreado** con `--load-balancers` (ECS no deja agregar LB a service existente) y `desired-count 1`, grace period 180s.
- ⚠️ Regla vieja `8080 0.0.0.0/0` en SG_ECS **no removida** todavía (opcional, ver guía A.1).

### 6.2 Frontend en S3 implementado (2026-06-01)
- Buckets static website: `chessquery-chess-portal` y `chessquery-organizer-panel` (us-east-1, lectura pública).
  - http://chessquery-chess-portal.s3-website-us-east-1.amazonaws.com
  - http://chessquery-organizer-panel.s3-website-us-east-1.amazonaws.com
- Build con `VITE_API_URL=<ALB DNS>` + Supabase Cloud (`.env.production`, gitignored, anon key).
- **CORS acotado** a los 2 orígenes S3 vía task-def rev **6** (env `GATEWAY_CORS_ALLOWED_ORIGINS`, ahora variable en la template).
- 🐞 **Bug encontrado y resuelto en el camino:** `GatewayConfig` usa `allowCredentials(true)`; con `GATEWAY_CORS_ALLOWED_ORIGINS=*` Spring lanzaba excepción en cada preflight → **500**. Se arregló poniendo orígenes **explícitos** (no `*`). Preflight ahora 200 con `Access-Control-Allow-Origin` correcto; origen no autorizado → 403.

### 6.3 Webhook user.registered activado (2026-06-01)
- Flujo: signup en Supabase → trigger `on_auth_user_registered_webhook` en `auth.users` →
  función `notify_user_registered()` (pg_net) → `POST /webhooks/supabase/user-registered`
  del gateway (header `X-Supabase-Webhook-Secret`) → publica `user.registered` a RabbitMQ →
  `ms-users` crea el perfil.
- **Config crítica del lado Supabase Cloud:** la 00003 apuntaba a `host.docker.internal`
  (no resuelve desde Cloud). Migración `00005` repunta la URL al ALB. El **secret** se inyectó
  redefiniendo la función desde el SQL Editor con el valor literal (Cloud bloquea
  `ALTER DATABASE ... SET app.settings.*` con error 42501).
- **Pre-requisito:** "Confirm email" **desactivado** en Auth (`mailer_autoconfirm=true`), si no
  el signup no devuelve sesión y el front falla al loguear.
- ✅ Verificado e2e: signup → perfil creado en `ms-users` (`/api/player/me/dashboard` → 200).

### 6.4 Auth end-to-end verificado (2026-06-01)
- Token real de Supabase Cloud es **ES256** (asimétrico); el gateway lo valida vía **JWKS**
  (`/auth/v1/.well-known/jwks.json`), con fallback HS256 al secret legacy.
- Cadena probada con datos reales: token → ALB → gateway (JWKS) → bff-player → ms-* → RDS.
- ⚠️ Quedan usuarios de prueba `smoke+*` / `webhooktest+*` en Supabase Auth (borrables desde
  Auth → Users).

> **Apagar/encender** (incluye lo nuevo): el ALB no cobra por estar idle de forma significativa, pero para ahorro total se puede dejar. RDS + ECS se apagan como en §1.6. El DNS del ALB **no cambia** al reencender.

---

## 7. Release v0.2.0 — features funcionales (pendiente de deploy)

Cambios de aplicación listos en la rama (commits sobre `feat/aws-deploy-fixes`),
**aún no desplegados** al cloud:

- **Invitación a partida** sin alta indebida en Supabase (solo push in-app + link).
- **Partidas en vivo de torneo**: al generar la ronda, ms-tournament crea una
  partida por emparejamiento en ms-game (con control de tiempo del torneo) y
  notifica a los jugadores; el organizador las observa en vivo (tablero
  espectador); al terminar, el resultado vuelve solo al pairing (consumer
  `game.finished`). Migraciones nuevas: **ms-game V5**, **ms-tournament V6**
  (aditivas, se aplican solas con Flyway al arrancar).
- **Login/Registro**: accesibilidad + recuperación de contraseña
  (`/forgot-password`, `/reset-password`).

### Procedimiento de redeploy (Fase B)
```bash
# 0) Credenciales Academy frescas en ~/.aws/credentials
# 1) (recomendado) smoke test e2e local con infrastructure/docker-compose.yml
# 2) Build + push de imágenes con tag nuevo
export IMAGE_TAG=v0.2.0
bash scripts/build-push-ecr.sh          # 8 imágenes → ECR (la task-def usa un único IMAGE_TAG)
# 3) Re-render task-def + update-service (mantener CORS explícito y el LB)
export SUPABASE_URL=https://pmtxxzscpactsgkijpul.supabase.co
export GATEWAY_CORS_ALLOWED_ORIGINS="http://chessquery-chess-portal.s3-website-us-east-1.amazonaws.com,http://chessquery-organizer-panel.s3-website-us-east-1.amazonaws.com"
#   envsubst de chessquery-stack.template.json → register-task-definition → update-service --force-new-deployment
#   (Flyway aplica V5 ms-game y V6 ms-tournament al arrancar)
# 4) Rebuild + sync de frontends a S3
cd frontend && npm run build -w @chessquery/ui-lib && npm run build -w chess-portal && npm run build -w organizer-panel && cd ..
aws s3 sync frontend/apps/chess-portal/dist/    s3://chessquery-chess-portal    --delete
aws s3 sync frontend/apps/organizer-panel/dist/ s3://chessquery-organizer-panel --delete
# 5) Verificar contra el ALB (readiness + flujo de torneo en vivo)
```
> Nota: el control de tiempo de la partida sale de `Tournament.timeControl` (texto
> libre "min+seg"); fallback 5+0. El consumer `game.finished` es idempotente y no
> entra en loop (solo actúa con `tournamentPairingId` y si el pairing no tiene
> resultado).
