# ChessQuery — Despliegue AWS Academy

> **Para profesores, clientes y el equipo (Agustín Castro + Martín Mora).**
> Este README explica en 5 minutos qué hay desplegado, dónde, y cómo
> reproducirlo.

---

## 1. Qué hay desplegado

ChessQuery corre en **AWS** con **ECS Fargate** como orquestador de
contenedores y **GitHub Actions** como pipeline CI/CD. El presupuesto
objetivo es de USD 50/mes por cuenta AWS Academy.

Hay dos cuentas AWS Academy disponibles, una por miembro del equipo:

| Cuenta              | Responsable     | Recursos                                              | Gasto aprox |
| ------------------- | --------------- | ----------------------------------------------------- | ----------- |
| **Principal**       | Agustín Castro  | ECS Cluster, ALB, RDS, ECR, Secrets Manager, RabbitMQ | ~USD 49/mes |
| **Frontend/backup** | Martín Mora     | S3 + CloudFront para `chess-portal` y `organizer-panel`, espejo opcional de imágenes ECR | <USD 2/mes |

> **Por qué no dividimos backend entre 2 cuentas.** Los microservicios
> necesitan hablarse entre sí + compartir BD. Duplicar ALB ($17) + RDS
> ($13) en ambas cuentas se come ~USD 60 del budget combinado sin
> aportar nada. Tener la cuenta de Martín como respaldo + frontend es
> más eficiente.

---

## 2. Arquitectura en una imagen

```
   Internet
       │
       ▼
 ┌─────────────────────────┐         ┌────────────────────────┐
 │ CloudFront (cuenta MM)  │         │  ALB (cuenta AC)       │
 │ chess-portal.s3 + org.. │         │  api.chessquery.cl     │
 └─────────────────────────┘         └────────────┬───────────┘
                                                   │
              ┌────────────────────────────────────┴────────────────┐
              │                                                     │
      ┌───────▼────────┐ ┌──────────┐ ┌──────────────┐ ┌────────────▼──┐
      │  api-gateway   │ │ ms-users │ │ ms-game      │ │ rabbitmq      │
      │  (Fargate)     │ │ (Spot)   │ │ (Fargate)    │ │ (Fargate)     │
      └───────┬────────┘ └─────┬────┘ └──────┬───────┘ └───────────────┘
              │                │             │
              └────────────────┴─────────────┘
                           ▼
              ┌────────────────────────┐
              │  RDS Postgres          │  ← multi-DB lógicas
              │  (users, game, etc.)   │
              └────────────────────────┘

  Supabase Auth + Storage (servicios externos en Supabase Cloud)
```

---

## 3. Pipeline CI/CD

Tres workflows en `.github/workflows/`:

| Workflow              | Cuándo corre                | Qué hace                                              |
| --------------------- | --------------------------- | ----------------------------------------------------- |
| `ci.yml`              | Cada push / PR              | Build + tests Java + tests frontend + tests BFFs      |
| `build-and-push.yml`  | Merge a `main` o manual     | Build Docker → push a ECR con tag `sha-XXXXXXX`+latest|
| `deploy.yml`          | Manual (`workflow_dispatch`)| Actualiza task definitions y dispara rollout en ECS   |

Estado actual:
- ✅ **CI** (build + tests): cubre `api-gateway`, `ms-users`, `ms-tournament`, `ms-game`, `ms-notifications`, `ms-analytics`, `chess-portal`, `organizer-panel`, `bff-player`, `bff-organizer`.
- ✅ **Build & push** parametrizado por matrix (8 servicios en paralelo).
- ✅ **Deploy** con `aws ecs register-task-definition` + `aws ecs update-service --force-new-deployment` + `aws ecs wait services-stable`.

---

## 4. Setup en la cuenta principal (Agustín)

Pre-requisitos: AWS CLI v2 + Docker + sesión Academy activa.

```bash
# 1. Variables de entorno
export AWS_ACCOUNT_ID=...
export AWS_REGION=us-east-1
export ECR_REGISTRY=${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com

# 2. Crear repos ECR (idempotente)
./infrastructure/scripts/setup-ecr.sh

# 3. Crear VPC + subnets + SG (manual desde consola la primera vez —
#    AWS Academy a veces bloquea CloudFormation).
#    Detalle paso-a-paso en infrastructure/aws/DEPLOY_ECS.md sección 3.

# 4. Crear cluster ECS Fargate
aws ecs create-cluster --cluster-name chessquery \
  --capacity-providers FARGATE FARGATE_SPOT \
  --default-capacity-provider-strategy capacityProvider=FARGATE_SPOT,weight=1

# 5. Aprovisionar RDS Postgres db.t3.micro Single-AZ
#    (consola → Postgres 15 → engine version más nueva 12 mo free tier).
#    Apuntar password al ARN de Secrets Manager.

# 6. Crear secrets en Secrets Manager
aws secretsmanager create-secret --name chessquery/db_password --secret-string "..."
aws secretsmanager create-secret --name chessquery/jwt_secret --secret-string "..."
aws secretsmanager create-secret --name chessquery/rabbitmq_password --secret-string "..."
aws secretsmanager create-secret --name chessquery/supabase_service_key --secret-string "..."
aws secretsmanager create-secret --name chessquery/supabase_webhook_secret --secret-string "..."

# 7. Configurar GitHub Secrets (Settings → Secrets and variables → Actions)
#    AWS_ACCOUNT_ID            (123456789012)
#    AWS_REGION                (us-east-1)
#    AWS_ACCESS_KEY_ID         (de las credenciales Academy)
#    AWS_SECRET_ACCESS_KEY     (ídem)
#    AWS_SESSION_TOKEN         (ídem — Academy las rota por sesión)
#    DB_HOST                   (endpoint RDS)
#    RABBITMQ_HOST             (rabbitmq.chessquery.local o privado)
#    SUPABASE_URL              (https://<proyecto>.supabase.co)
#    *_ARN                     (ARNs de Secrets Manager del paso 6)
#    TASK_EXECUTION_ROLE_ARN   (ARN del rol creado en consola IAM)

# 8. Trigger manual del primer build & push
gh workflow run build-and-push.yml

# 9. Crear los services en ECS (manual la primera vez; los siguientes
#    deploys se hacen vía workflow deploy.yml).
aws ecs create-service --cluster chessquery --service-name api-gateway \
  --task-definition chessquery-api-gateway --desired-count 1 \
  --launch-type FARGATE --network-configuration ...

# 10. Trigger deploy.yml por la consola GitHub Actions
#     Inputs: image_tag=latest, service=todos, cluster=chessquery
```

---

## 5. Setup mínimo en la cuenta del compañero (Martín Mora)

**Rol:** servir los frontends estáticos y mantener un espejo de las
imágenes ECR como respaldo. Ningún microservicio backend corre acá.

### Pasos (15 min)

```bash
# Variables
export AWS_ACCOUNT_ID=<tu_account_id_martin>
export AWS_REGION=us-east-1
export DOMAIN=chessquery-mm.example  # o sin custom domain

# 1. Crear 2 buckets S3 estáticos
aws s3api create-bucket --bucket chessquery-chess-portal-mm --region us-east-1
aws s3api create-bucket --bucket chessquery-organizer-panel-mm --region us-east-1

# 2. Habilitar website hosting
aws s3 website s3://chessquery-chess-portal-mm/ \
  --index-document index.html --error-document index.html
aws s3 website s3://chessquery-organizer-panel-mm/ \
  --index-document index.html --error-document index.html

# 3. Política pública de lectura (o, mejor, usar Origin Access Control desde CloudFront)
#    Detalle en https://docs.aws.amazon.com/AmazonS3/latest/userguide/WebsiteHosting.html

# 4. Build de los frontends (desde el repo, en cualquier máquina con Node 20)
cd frontend
npm ci
VITE_API_URL=https://api.chessquery.cl npm --prefix apps/chess-portal run build
VITE_API_URL=https://api.chessquery.cl npm --prefix apps/organizer-panel run build

# 5. Subir a S3 (sincroniza diff, idempotente)
aws s3 sync apps/chess-portal/dist/    s3://chessquery-chess-portal-mm/    --delete
aws s3 sync apps/organizer-panel/dist/ s3://chessquery-organizer-panel-mm/ --delete

# 6. Crear distribución CloudFront por consola apuntando a cada bucket
#    - Default cache behavior: redirect HTTP → HTTPS
#    - Custom error response: 403 y 404 → /index.html con código 200
#      (necesario para SPAs con react-router en modo BrowserRouter)
```

### Espejo de imágenes ECR (opcional, para failover)

Si la cuenta principal queda sin crédito o se cuelga la sesión, podemos
recrear el stack en la cuenta de Martín. Cada vez que el workflow
`build-and-push.yml` pushea imágenes, Martín puede correr este script
para tener el mirror local:

```bash
# En la cuenta de Martín
export ECR_REGISTRY_MIRROR=${AWS_ACCOUNT_ID}.dkr.ecr.us-east-1.amazonaws.com
export ECR_REGISTRY_ORIGIN=<account_id_agustin>.dkr.ecr.us-east-1.amazonaws.com

for svc in api-gateway ms-users ms-tournament ms-game ms-notifications ms-analytics; do
  # Pull desde la cuenta principal (cross-account, requiere policy)
  docker pull ${ECR_REGISTRY_ORIGIN}/chessquery/${svc}:latest
  docker tag  ${ECR_REGISTRY_ORIGIN}/chessquery/${svc}:latest \
              ${ECR_REGISTRY_MIRROR}/chessquery/${svc}:latest
  docker push ${ECR_REGISTRY_MIRROR}/chessquery/${svc}:latest
done
```

### División de tareas sugerida en clase

| Tarea                                       | Responsable    |
| ------------------------------------------- | -------------- |
| Cluster ECS + Fargate services              | Agustín        |
| RDS Postgres + migraciones                  | Agustín        |
| Secrets Manager + IAM roles                 | Agustín        |
| Configurar GitHub Secrets en el repo        | Agustín        |
| Frontends a S3 + CloudFront                 | **Martín**     |
| Verificar healthchecks ALB durante demo     | **Martín**     |
| Documentar pasos para el informe escrito    | Ambos          |
| Demo en vivo: levantar `desired-count=1` antes de presentar | Agustín |

---

## 6. Cómo opera el CI/CD en el día a día

### Push a una rama / PR
- Corre `ci.yml`. Si rompe tests, no merge. Si pasa, ✅ en la PR.

### Merge a `main`
- Corre `ci.yml` otra vez (smoke).
- Corre `build-and-push.yml`: 8 servicios buildeados en paralelo, push a ECR con tag `sha-<commit>` y `latest`.

### Desplegar a ECS
- Manual desde GitHub Actions UI → `deploy.yml` → "Run workflow".
- Inputs:
  - `image_tag`: `latest` o `sha-abc1234` para promover una imagen específica.
  - `service`: `todos` o nombre puntual (`api-gateway`, `ms-users`, …).
  - `cluster`: `chessquery` (por defecto).
- El workflow:
  1. Renderiza la task definition (`envsubst` reemplaza placeholders).
  2. `aws ecs register-task-definition` → nueva revisión.
  3. `aws ecs update-service --force-new-deployment`.
  4. `aws ecs wait services-stable` (timeout ~10 min).

### Rollback rápido
- Re-correr `deploy.yml` con `image_tag=sha-<commit-anterior>`.
- Cada deploy crea una nueva revisión de task definition; las anteriores quedan inmutables.

---

## 7. Estimación de costos

| Recurso                                                      | USD/mes |
| ------------------------------------------------------------ | ------- |
| ECS Fargate (5 tasks Spot + 1 normal, 0.25-0.5 vCPU)         | ~10     |
| ECS Fargate RabbitMQ (0.5 vCPU / 1 GB always-on)             | ~6      |
| Application Load Balancer                                    | 17      |
| RDS Postgres `db.t3.micro` Single-AZ 20 GB                   | 13      |
| ECR (5 GB almacenados)                                       | <1      |
| S3 + CloudFront (cuenta Martín)                              | <2      |
| Secrets Manager (5 secrets)                                  | 2       |
| **TOTAL (ambas cuentas)**                                    | **~51** |

> Si nos pasamos del budget, primero apagar tasks fuera de horario de demo:
>
> ```bash
> aws ecs update-service --cluster chessquery --service api-gateway --desired-count 0
> ```
>
> Eso recorta Fargate al 0% durante las horas dormidas y vuelve a 1
> cuando arranquemos la próxima demo.

---

## 8. Documentación relacionada

- `infrastructure/aws/DEPLOY_ECS.md` — plan detallado con justificación
  arquitectónica, riesgos y checklist completo.
- `infrastructure/DEPLOY.md` — variante anterior basada en k3s + EC2
  (preservada por compatibilidad; no es la que está activa).
- `infrastructure/aws/task-definitions/*.template.json` — definiciones
  ECS parametrizadas.
- `.github/workflows/*.yml` — los tres pipelines.
- `infrastructure/scripts/setup-ecr.sh` — crea repos ECR (vale para
  ambas cuentas).
