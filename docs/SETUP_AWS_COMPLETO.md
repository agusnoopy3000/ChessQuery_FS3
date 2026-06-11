# ChessQuery — Setup completo de AWS desde cero (para replicar en otra cuenta)

> Guía para que **otro integrante** levante TODA la infra de ChessQuery en **su propia
> cuenta de AWS Academy**, desde su computador y consola. Incluye comandos, rutas y las
> consideraciones que más cuestan caro si se pasan por alto.

---

## ⚠️ LEER PRIMERO — consideraciones críticas

1. **Cada cuenta de AWS Academy es independiente.** Si replicás en tu cuenta, vas a generar
   recursos **nuevos** con **endpoints distintos** (otro `ACCOUNT_ID`, otro DNS de ALB, otro
   host de RDS). **No** vas a compartir la infra del compañero original; es un entorno paralelo.
   → Consecuencia: al final hay que **re-apuntar el frontend y el CORS** a TUS endpoints (§6–§7).

2. **Credenciales de Academy rotan cada ~4 h** y al expirar entran en modo `voc-cancel-cred`
   (deniegan todo). Si un script se corta a la mitad, **refrescá creds y volvé a correrlo**:
   `setup-aws.sh` es **idempotente** (retoma donde quedó).

3. **AWS Academy bloquea varias cosas:** Cloud Map / service discovery, IAM (solo se usa el rol
   `LabRole` ya existente), y a veces OIDC. Por eso el diseño es **una sola task ECS con 10
   contenedores que se hablan por `localhost`** (no hay DNS interno). **Límite duro: 10
   contenedores por task** (por eso `ms-etl` quedó fuera).

4. **Base de datos:** los microservicios corren con `ddl-auto=none`. El esquema lo crean las
   **migraciones Flyway** al arrancar cada servicio. No hay que crear tablas a mano (sí las
   **bases** vacías, que las crea `setup-aws.sh`).

5. **Builds de imágenes en Mac M1 / Apple Silicon:** ECS Fargate corre en **x86_64**. En M1,
   `docker build` genera **arm64** y NO arranca en Fargate. Exportá
   `export DOCKER_DEFAULT_PLATFORM=linux/amd64` antes de buildear (ver §5).

6. **Secretos:** nunca se commitean. Viven en **AWS Secrets Manager** y el archivo
   `infrastructure/aws/.deploy-outputs.env` (que es **gitignored**). Necesitás las claves de
   **Supabase** (§3) para provisionar.

---

## 0. Herramientas necesarias (en tu computador)

**Mac (Apple Silicon):**
```bash
brew install git gh awscli jq node@22 maven openjdk@17 postgresql@16 gettext
brew install --cask docker        # Docker Desktop — arrancalo a mano
export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/node@22/bin:/opt/homebrew/opt/postgresql@16/bin:/opt/homebrew/opt/gettext/bin:$PATH"
```

**Arch Linux:**
```bash
# Repos oficiales
sudo pacman -S --needed git github-cli jq nodejs npm maven jdk17-openjdk \
  postgresql docker docker-buildx gettext
# aws-cli v2: está en AUR (con yay) o usá el instalador oficial
yay -S aws-cli-v2          # opción A (AUR)
# opción B sin AUR:
#   curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
#   cd /tmp && unzip awscliv2.zip && sudo ./aws/install

# Java 17 por defecto + JAVA_HOME
sudo archlinux-java set java-17-openjdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export PATH="$JAVA_HOME/bin:$PATH"

# Docker: habilitar el servicio y permitir tu usuario sin sudo
sudo systemctl enable --now docker.service
sudo usermod -aG docker "$USER"     # cerrar sesión y volver a entrar para que aplique
```
> Notas Arch:
> - El binario `psql` viene en el paquete **`postgresql`** (no hace falta el server, pero el
>   paquete lo incluye; alcanza para conectarse a la RDS).
> - `nodejs` en Arch suele ser ≥22 (sirve). Si necesitás fijar una versión, usá `nvm`.
> - `gettext` aporta **`envsubst`** (lo usa `create-ecs-service.sh`).
> - **No necesitás** `DOCKER_DEFAULT_PLATFORM=linux/amd64` (Arch x86_64 ya buildea amd64);
>   ese flag es solo para Mac M1.

**Debian/Ubuntu/Fedora:** instalar los equivalentes `git gh awscli jq nodejs(22) maven
openjdk-17 postgresql-client docker gettext` con `apt`/`dnf`.

Verificar (cualquier distro):
```bash
aws --version && java -version && mvn -v && node -v && docker version && psql --version && envsubst --version
```

---

## 1. Clonar el repo
```bash
mkdir -p ~/dev && cd ~/dev
git clone https://github.com/agusnoopy3000/ChessQuery_FS3.git
cd ChessQuery_FS3
```

---

## 2. Configurar credenciales de AWS Academy
En AWS Academy → "AWS Details" → copiar las 3 líneas (rotan cada ~4 h):
```bash
aws configure set aws_access_key_id     <ID>
aws configure set aws_secret_access_key <SECRET>
aws configure set aws_session_token     <TOKEN>
aws configure set region us-east-1
aws sts get-caller-identity     # confirma TU número de cuenta
```

---

## 3. Exportar variables de Supabase (obligatorias para provisionar)
Las saca del proyecto Supabase (Settings → API / JWT):
```bash
export SUPABASE_URL=https://<tu-ref>.supabase.co
export SUPABASE_SERVICE_KEY=<service_role key>     # ¡secreto! da acceso admin
export SUPABASE_JWT_SECRET=<Legacy JWT secret>
```
> **Modo réplica (redundancia):** cada uno usa su **propio proyecto Supabase**. Cómo crearlo y
> conectarlo a TU ALB (claves, migraciones, webhook, `pg_net`, ajustes de Auth) está en
> **`docs/SETUP_SUPABASE_REPLICA.md`** — hacelo en paralelo a este §3, porque `setup-aws.sh`
> necesita estas 3 variables y el webhook se configura una vez que tengas tu ALB (§7).

---

## 4. Provisionar la infra base (ECR + ECS cluster + RDS + 6 BDs + Secrets)
```bash
bash scripts/setup-aws.sh
```
Qué hace (idempotente, ~8–10 min por la RDS):
- Crea **9 repos ECR** (uno por servicio).
- Crea el **cluster ECS Fargate** (`chessquery-cluster`).
- Crea **RDS PostgreSQL 16** (`chessquery-pg`, `db.t4g.micro`, pública) + su Security Group
  (abre 5432 a la VPC y a tu IP) + las **6 bases** vacías (`users_db`, `tournament_db`,
  `game_db`, `notifications_db`, `analytics_db`, `etl_db`).
- Genera secretos en **Secrets Manager** (`db-password`, `rabbitmq-password`,
  `supabase-service-key`, `jwt-secret`, `supabase-webhook-secret`).
- Escribe todos los ARNs/endpoints en **`infrastructure/aws/.deploy-outputs.env`** (gitignored).

> Este archivo es la "memoria" del entorno. **Guardalo / no lo borres.** Los pasos siguientes
> lo leen.

---

## 5. Construir y subir las imágenes a ECR
```bash
# Mac M1: forzar arquitectura de Fargate
export DOCKER_DEFAULT_PLATFORM=linux/amd64      # (omitir en Linux x86)

export IMAGE_TAG=v0.2.4
bash scripts/build-push-ecr.sh
```
Buildea y pushea las 8 imágenes de servicio (gateway, 5 ms, 2 bff). Requiere Docker corriendo
y creds AWS vigentes.

> Compilar/test del backend antes (opcional): en cada módulo
> `cd ms-users && mvn -q -DskipTests package` (Java 17 + Maven).

---

## 6. Crear el service ECS (task única de 10 contenedores)
```bash
export IMAGE_TAG=v0.2.4
export SUPABASE_URL=https://<tu-ref>.supabase.co
bash scripts/create-ecs-service.sh
```
Renderiza `infrastructure/aws/task-definitions/chessquery-stack.template.json` con tus ARNs,
registra la task-def, crea el SG de ECS (abre 8080) y crea el **service `chessquery-stack`**
con IP pública, `desired-count=1`.

> Flyway aplica las migraciones al arrancar. Esperá a que la task quede sana (§9).

---

## 7. Poner el ALB delante del gateway (URL estable)
El `create-ecs-service.sh` deja el gateway con IP pública efímera. Para una URL fija se agrega
un ALB. Pasos detallados en **`docs/FRONTEND_S3_Y_ALB.md` §A**. Resumen:
```bash
source infrastructure/aws/.deploy-outputs.env
# 1) SG del ALB (80 desde internet) + permitir 8080 desde el SG del ALB hacia la task
# 2) create-load-balancer  →  ALB_ARN
# 3) create-target-group (tipo IP, puerto 8080, health-check /actuator/health/readiness) → TG_ARN
# 4) create-listener :80 → forward al TG
# 5) recrear el service con --load-balancers apuntando a api-gateway:8080
# 6) DNS estable:
aws elbv2 describe-load-balancers --names chessquery-alb --query 'LoadBalancers[0].DNSName' --output text
```
> Guardá `ALB_ARN` y `TG_ARN` en `.deploy-outputs.env`. **Ese DNS de ALB es TUYO y distinto**
> al del entorno original.

---

## 8. Frontends en S3
Detalle en **`docs/FRONTEND_S3_Y_ALB.md` §B**. Resumen:
```bash
# Build apuntando a TU ALB (clave: la URL de la API):
export VITE_API_BASE_URL=http://<TU-ALB-DNS>
cd frontend && npm install && npm run build && cd ..

# Crear buckets website + policy pública + subir:
aws s3 sync frontend/apps/chess-portal/dist/    s3://<tu-bucket-portal>    --delete
aws s3 sync frontend/apps/organizer-panel/dist/ s3://<tu-bucket-organizer> --delete
```
Luego **agregá las URLs S3 al CORS del gateway** (`GATEWAY_CORS_ALLOWED_ORIGINS` en la
task-def) y redesplegá, si no el navegador bloquea las llamadas.

---

## 9. Verificar
```bash
source infrastructure/aws/.deploy-outputs.env
aws ecs describe-services --cluster chessquery-cluster --services chessquery-stack \
  --query 'services[0].{Running:runningCount,Desired:desiredCount}'
aws elbv2 describe-target-health --target-group-arn "$TG_ARN" \
  --query 'TargetHealthDescriptions[0].TargetHealth.State' --output text   # → healthy
curl http://<TU-ALB-DNS>/actuator/health/readiness                          # → {"status":"UP"}
```

---

## 10. Operación diaria (prender / apagar para no gastar)
```bash
# PRENDER
aws rds start-db-instance --db-instance-identifier chessquery-pg
# esperar 'available'...
aws ecs update-service --cluster chessquery-cluster --service chessquery-stack \
  --desired-count 1 --force-new-deployment

# APAGAR
aws ecs update-service --cluster chessquery-cluster --service chessquery-stack --desired-count 0
aws rds stop-db-instance --db-instance-identifier chessquery-pg
```
> Lo caro es **ECS (Fargate)** + **RDS**: esos se apagan. ALB y S3 cuestan centavos y quedan
> activos (así el DNS no cambia).

---

## Orden resumido
```
0. Instalar tooling          → §0
1. git clone                 → §1
2. aws configure (creds)     → §2
3. export SUPABASE_*         → §3
4. setup-aws.sh              → §4   (ECR, cluster, RDS+6 BDs, Secrets, .deploy-outputs.env)
5. build-push-ecr.sh         → §5   (⚠️ linux/amd64 en M1)
6. create-ecs-service.sh     → §6   (task-def + service)
7. ALB                       → §7   (docs/FRONTEND_S3_Y_ALB.md §A)
8. S3 frontends + CORS       → §8   (docs/FRONTEND_S3_Y_ALB.md §B)
9. verificar                 → §9
10. operar (on/off)          → §10
```

## Referencias en el repo
- `scripts/setup-aws.sh` — provisión base (idempotente).
- `scripts/build-push-ecr.sh` — build/push de imágenes.
- `scripts/create-ecs-service.sh` — task-def + service.
- `infrastructure/aws/task-definitions/chessquery-stack.template.json` — definición de los 10 contenedores.
- `docs/FRONTEND_S3_Y_ALB.md` — ALB + S3 paso a paso.
- `docs/SUPABASE_SETUP.md` — Auth, webhook, constraints.
- `docs/INFRA_PARA_EL_EQUIPO.md` — visión general no técnica.
