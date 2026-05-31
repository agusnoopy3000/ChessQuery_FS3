# RUNBOOK — Despliegue ChessQuery en AWS ECS Fargate (paso a paso)

Guía operativa para llevar el proyecto **desde cero** hasta el primer despliegue en AWS ECS, usando GitHub Actions para CI/CD.

> Audiencia: Agustín Castro + Martín Mora (cuentas AWS Academy ~$50).
> Lee primero [`README.md`](README.md) (visión general) y [`DEPLOY_ECS.md`](DEPLOY_ECS.md) (plan arquitectónico + costos).
>
> **Alcance del deploy demo:** 6 microservicios Java (api-gateway, ms-users, ms-tournament, ms-game, ms-notifications, ms-analytics) + RabbitMQ + 2 frontends (chess-portal, organizer-panel).
> **Fuera de alcance:** `bff-admin` (sin vista admin operativa), `bff-player`/`bff-organizer` (orquestación liviana — opcional para la demo), `ms-etl` (Python, opt-in profile).

---

## 0. Pre-requisitos locales

```bash
aws --version           # >= 2.15
docker --version        # >= 24
jq --version
gh --version            # GitHub CLI autenticado
```

Variables que usaremos en toda la guía:

```bash
export AWS_REGION=us-east-1
export AWS_ACCOUNT_ID=<tu-account-id-academy>      # aws sts get-caller-identity --query Account --output text
export ECR_REGISTRY=$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com
export PROJECT=chessquery
```

AWS Academy: las credenciales caducan cada 4h. Cuando expiren, copia de nuevo de "AWS Details > AWS CLI" a `~/.aws/credentials` (perfil `default`).

---

## 1. Configuración inicial AWS (una sola vez por cuenta)

### 1.1 Crear roles IAM mínimos

AWS Academy ya provee `LabRole`. **Reusamos ese rol** como `executionRoleArn` y `taskRoleArn` para evitar restricciones.

```bash
export TASK_EXECUTION_ROLE_ARN=arn:aws:iam::$AWS_ACCOUNT_ID:role/LabRole
```

> Si en tu Academy no existe `LabRole`, créalo con políticas `AmazonECSTaskExecutionRolePolicy` + `SecretsManagerReadWrite` + `CloudWatchLogsFullAccess`.

### 1.2 Crear repositorios ECR (uno por servicio)

```bash
for svc in api-gateway ms-users ms-tournament ms-game ms-notifications ms-analytics ms-etl bff-player bff-organizer; do
  aws ecr create-repository \
    --repository-name $PROJECT/$svc \
    --region $AWS_REGION \
    --image-scanning-configuration scanOnPush=true \
    --image-tag-mutability MUTABLE 2>/dev/null || echo "$svc ya existe"
done
```

### 1.3 Crear cluster ECS Fargate

```bash
aws ecs create-cluster \
  --cluster-name $PROJECT-cluster \
  --capacity-providers FARGATE FARGATE_SPOT \
  --default-capacity-provider-strategy capacityProvider=FARGATE_SPOT,weight=4 capacityProvider=FARGATE,weight=1 \
  --region $AWS_REGION
```

### 1.4 Service Discovery (Cloud Map)

Para que los microservicios se llamen entre sí por DNS interno (`ms-users.chessquery.local`):

```bash
# Necesitas el VPC ID (default VPC del Academy o uno creado)
export VPC_ID=$(aws ec2 describe-vpcs --filters Name=isDefault,Values=true --query 'Vpcs[0].VpcId' --output text)

aws servicediscovery create-private-dns-namespace \
  --name $PROJECT.local \
  --vpc $VPC_ID \
  --region $AWS_REGION
# Espera ~60s y obten el ID
export NAMESPACE_ID=$(aws servicediscovery list-namespaces --query "Namespaces[?Name=='$PROJECT.local'].Id" --output text)
```

### 1.5 RDS PostgreSQL único (con múltiples DBs)

```bash
# Security group para RDS abierto a la VPC
aws ec2 create-security-group --group-name $PROJECT-rds-sg --description "RDS ChessQuery" --vpc-id $VPC_ID
export SG_RDS=$(aws ec2 describe-security-groups --filters Name=group-name,Values=$PROJECT-rds-sg --query 'SecurityGroups[0].GroupId' --output text)
aws ec2 authorize-security-group-ingress --group-id $SG_RDS --protocol tcp --port 5432 --cidr 10.0.0.0/8

# Generar password y guardarla en Secrets Manager
export DB_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=')
aws secretsmanager create-secret --name $PROJECT/db-password --secret-string "$DB_PASSWORD" --region $AWS_REGION

# Crear instancia (~$13/mes db.t4g.micro)
aws rds create-db-instance \
  --db-instance-identifier $PROJECT-pg \
  --db-instance-class db.t4g.micro \
  --engine postgres --engine-version 16.4 \
  --master-username chessquery --master-user-password "$DB_PASSWORD" \
  --allocated-storage 20 --storage-type gp3 \
  --vpc-security-group-ids $SG_RDS \
  --backup-retention-period 1 --no-multi-az --publicly-accessible \
  --region $AWS_REGION

# Esperar ~8 min y obtener endpoint
aws rds wait db-instance-available --db-instance-identifier $PROJECT-pg
export DB_HOST=$(aws rds describe-db-instances --db-instance-identifier $PROJECT-pg --query 'DBInstances[0].Endpoint.Address' --output text)

# Crear las 6 bases (psql desde tu máquina)
for db in users_db tournament_db game_db notifications_db analytics_db etl_db; do
  PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -U chessquery -d postgres -c "CREATE DATABASE $db;"
done
```

### 1.6 Secrets Manager (resto de credenciales)

```bash
aws secretsmanager create-secret --name $PROJECT/rabbitmq-password     --secret-string "$(openssl rand -base64 24 | tr -d '/+=')"
aws secretsmanager create-secret --name $PROJECT/supabase-service-key   --secret-string "<tu-supabase-service-key>"
aws secretsmanager create-secret --name $PROJECT/jwt-secret             --secret-string "<JWT secret de Supabase: Project Settings > API > JWT Secret>"
aws secretsmanager create-secret --name $PROJECT/supabase-webhook-secret --secret-string "<secret del webhook Supabase → ms-users>"

# Guarda los ARNs (los necesitan los task definitions y deploy.yml)
export DB_PASSWORD_ARN=$(aws secretsmanager describe-secret --secret-id $PROJECT/db-password --query ARN --output text)
export RABBITMQ_PASSWORD_ARN=$(aws secretsmanager describe-secret --secret-id $PROJECT/rabbitmq-password --query ARN --output text)
export SUPABASE_SERVICE_KEY_ARN=$(aws secretsmanager describe-secret --secret-id $PROJECT/supabase-service-key --query ARN --output text)
export JWT_SECRET_ARN=$(aws secretsmanager describe-secret --secret-id $PROJECT/jwt-secret --query ARN --output text)
export SUPABASE_WEBHOOK_SECRET_ARN=$(aws secretsmanager describe-secret --secret-id $PROJECT/supabase-webhook-secret --query ARN --output text)
```

> **Nota:** el `jwt-secret` debe ser el **mismo JWT Secret de tu proyecto Supabase Cloud**
> (Project Settings → API → JWT Secret), no uno aleatorio — el api-gateway valida los
> tokens emitidos por Supabase contra él.

---

## 2. Configurar GitHub Actions (una sola vez)

### 2.1 Secrets del repositorio

```bash
# Credenciales AWS (las consumen build-and-push.yml y deploy.yml)
gh secret set AWS_ACCESS_KEY_ID      --body "<de Academy>"
gh secret set AWS_SECRET_ACCESS_KEY  --body "<de Academy>"
gh secret set AWS_SESSION_TOKEN      --body "<de Academy, obligatorio en Academy>"
gh secret set AWS_REGION             --body "$AWS_REGION"
gh secret set AWS_ACCOUNT_ID         --body "$AWS_ACCOUNT_ID"

# Datos de infra que deploy.yml inyecta en las task definitions (envsubst)
gh secret set TASK_EXECUTION_ROLE_ARN     --body "$TASK_EXECUTION_ROLE_ARN"
gh secret set DB_HOST                     --body "$DB_HOST"
gh secret set RABBITMQ_HOST               --body "rabbitmq.$PROJECT.local"
gh secret set SUPABASE_URL                --body "https://<tu-proyecto>.supabase.co"
gh secret set DB_PASSWORD_ARN             --body "$DB_PASSWORD_ARN"
gh secret set RABBITMQ_PASSWORD_ARN       --body "$RABBITMQ_PASSWORD_ARN"
gh secret set SUPABASE_SERVICE_KEY_ARN    --body "$SUPABASE_SERVICE_KEY_ARN"
gh secret set JWT_SECRET_ARN              --body "$JWT_SECRET_ARN"
gh secret set SUPABASE_WEBHOOK_SECRET_ARN --body "$SUPABASE_WEBHOOK_SECRET_ARN"
```

> ⚠️ Las credenciales del Academy caducan (TTL 4h); refresca `AWS_ACCESS_KEY_ID`,
> `AWS_SECRET_ACCESS_KEY` y `AWS_SESSION_TOKEN` cada sesión larga. Los ARNs e infra
> (DB_HOST, *_ARN, etc.) son estables y se setean una sola vez.

### 2.2 Verificar workflows ya commiteados

```bash
ls .github/workflows/
# ci.yml               -> tests (corre en cada push/PR)
# build-and-push.yml   -> construye imágenes y publica en ECR
# deploy.yml           -> registra task definitions y actualiza services ECS
```

---

## 3. Primer build & push (manual)

Ejecuta el workflow desde la UI o CLI:

```bash
gh workflow run build-and-push.yml -f tag=v0.1.0
gh run watch
```

Verifica imágenes en ECR:

```bash
aws ecr describe-images --repository-name $PROJECT/ms-users --query 'imageDetails[].imageTags'
```

---

## 4. Primer deploy (paso por servicio)

### 4.1 Renderizar task definitions desde plantillas

```bash
cd infrastructure/aws/task-definitions
export ECR_REGISTRY DB_HOST=$DB_HOST RABBITMQ_HOST=rabbitmq.$PROJECT.local \
       TASK_EXECUTION_ROLE_ARN AWS_REGION IMAGE_TAG=v0.1.0 \
       DB_PASSWORD_ARN RABBITMQ_PASSWORD_ARN SUPABASE_SERVICE_KEY_ARN \
       JWT_SECRET_ARN SUPABASE_WEBHOOK_SECRET_ARN \
       SUPABASE_URL=https://<tu-proyecto>.supabase.co

for f in *.template.json; do
  envsubst < $f > rendered-${f%.template.json}.json
done
```

### 4.2 Registrar las task definitions

```bash
for f in rendered-*.json; do
  aws ecs register-task-definition --cli-input-json file://$f
done
```

### 4.3 Crear los services ECS (orden: rabbitmq → ms-users → resto → api-gateway)

```bash
# Subnets de la VPC default + un SG con todo el tráfico interno permitido
export SUBNETS=$(aws ec2 describe-subnets --filters Name=vpc-id,Values=$VPC_ID --query 'Subnets[].SubnetId' --output text | tr '\t' ',')
aws ec2 create-security-group --group-name $PROJECT-ecs-sg --description "ECS internal" --vpc-id $VPC_ID
export SG_ECS=$(aws ec2 describe-security-groups --filters Name=group-name,Values=$PROJECT-ecs-sg --query 'SecurityGroups[0].GroupId' --output text)
aws ec2 authorize-security-group-ingress --group-id $SG_ECS --protocol -1 --source-group $SG_ECS
aws ec2 authorize-security-group-ingress --group-id $SG_ECS --protocol tcp --port 8080 --cidr 0.0.0.0/0   # api-gateway público

# Función helper
create_service() {
  local SVC=$1 PORT=$2
  # Service Discovery
  local SD_ARN=$(aws servicediscovery create-service \
    --name $SVC --namespace-id $NAMESPACE_ID \
    --dns-config "NamespaceId=$NAMESPACE_ID,DnsRecords=[{Type=A,TTL=10}]" \
    --query 'Service.Arn' --output text)
  # Service ECS
  aws ecs create-service \
    --cluster $PROJECT-cluster --service-name $SVC \
    --task-definition $PROJECT-$SVC \
    --desired-count 1 --launch-type FARGATE \
    --network-configuration "awsvpcConfiguration={subnets=[$SUBNETS],securityGroups=[$SG_ECS],assignPublicIp=ENABLED}" \
    --service-registries "registryArn=$SD_ARN"
}

create_service rabbitmq         5672
sleep 60   # esperar que arranque rabbitmq antes de los MS
create_service ms-users         8081
create_service ms-tournament    8082
create_service ms-game          8083
create_service ms-notifications 8085
create_service ms-analytics     8084
create_service api-gateway      8080
```

### 4.4 Verificar

```bash
aws ecs list-services --cluster $PROJECT-cluster
aws ecs describe-services --cluster $PROJECT-cluster --services api-gateway \
  --query 'services[0].{Running:runningCount,Desired:desiredCount,Status:status}'

# IP pública del api-gateway
TASK=$(aws ecs list-tasks --cluster $PROJECT-cluster --service-name api-gateway --query 'taskArns[0]' --output text)
ENI=$(aws ecs describe-tasks --cluster $PROJECT-cluster --tasks $TASK \
  --query 'tasks[0].attachments[0].details[?name==`networkInterfaceId`].value' --output text)
aws ec2 describe-network-interfaces --network-interface-ids $ENI \
  --query 'NetworkInterfaces[0].Association.PublicIp' --output text

# Probar health
curl http://<PUBLIC_IP>:8080/actuator/health
```

---

## 5. Deploys posteriores (totalmente automatizados)

Cualquier push a `main`:

1. **ci.yml** corre tests (matriz java + frontend + bff)
2. Manualmente: `gh workflow run build-and-push.yml -f tag=$(git rev-parse --short HEAD)`
3. Manualmente: `gh workflow run deploy.yml -f image_tag=$(git rev-parse --short HEAD) -f service=ms-users -f cluster=chessquery-cluster`

El workflow `deploy.yml` ejecuta:
- `envsubst` sobre la plantilla del servicio
- `aws ecs register-task-definition`
- `aws ecs update-service --force-new-deployment`
- `aws ecs wait services-stable`

---

## 6. Frontend (responsabilidad de Martín — opcional segunda cuenta)

```bash
# Build estáticos
cd frontend && npm run build

# Bucket S3 + CloudFront (una sola vez)
aws s3 mb s3://$PROJECT-chess-portal --region $AWS_REGION
aws s3 website s3://$PROJECT-chess-portal --index-document index.html --error-document index.html

# Sync (cada deploy)
aws s3 sync apps/chess-portal/dist/ s3://$PROJECT-chess-portal --delete
aws s3 sync apps/organizer-panel/dist/ s3://$PROJECT-organizer-panel --delete
```

---

## 7. Smoke test E2E

```bash
export GW=http://<PUBLIC_IP>:8080

# 1. Registro vía Supabase (frontend)
# 2. Token Supabase → API Gateway
curl -H "Authorization: Bearer $TOKEN" $GW/api/users/me
curl -H "Authorization: Bearer $TOKEN" $GW/api/tournaments
```

Si los 3 health checks (`/actuator/health` de api-gateway, ms-users, ms-tournament) responden `UP` y un GET autenticado funciona, el despliegue está OK.

---

## 8. Apagado para ahorrar saldo

```bash
# Bajar tasks a 0 (no eliminar)
for s in api-gateway ms-users ms-tournament ms-game ms-notifications ms-analytics rabbitmq; do
  aws ecs update-service --cluster $PROJECT-cluster --service $s --desired-count 0
done

# Detener RDS (hasta 7 días)
aws rds stop-db-instance --db-instance-identifier $PROJECT-pg
```

Para reanudar: `--desired-count 1` y `start-db-instance`.

---

## 9. Troubleshooting rápido

| Síntoma | Causa probable | Fix |
|---|---|---|
| Task se queda en `PROVISIONING` | Sin IP pública o subnet privada sin NAT | `assignPublicIp=ENABLED` |
| `CannotPullContainerError` | Falta permiso ECR en role | Adjuntar `AmazonEC2ContainerRegistryReadOnly` al `LabRole` |
| Health check FAIL | App tarda > startPeriod | Subir `startPeriod` en la template a 120 |
| `Connection refused` entre MS | Service Discovery no resuelve | Verificar `--service-registries` en create-service |
| `Access denied` en Secrets Manager | Role sin permisos | Adjuntar `SecretsManagerReadWrite` |
| Credenciales Academy expiradas | TTL 4h | Renovar `~/.aws/credentials` y `gh secret set` |

---

## 10. Checklist resumen (imprimible)

- [ ] Variables exportadas (`AWS_REGION`, `AWS_ACCOUNT_ID`, `ECR_REGISTRY`)
- [ ] Repos ECR creados (9 servicios)
- [ ] Cluster ECS Fargate creado
- [ ] Namespace Cloud Map `chessquery.local`
- [ ] RDS `chessquery-pg` + 6 bases creadas
- [ ] Secrets Manager: db-password, rabbitmq-password, supabase-service-key, jwt-secret, supabase-webhook-secret
- [ ] GitHub secrets configurados (AWS_*, ARNs, DB_HOST, RABBITMQ_HOST, SUPABASE_URL, TASK_EXECUTION_ROLE_ARN)
- [ ] Workflow `build-and-push.yml` ejecutado → imágenes en ECR
- [ ] Task definitions renderizadas + registradas
- [ ] 7 services ECS creados (orden: rabbitmq → MS → api-gateway)
- [ ] Health checks UP
- [ ] Smoke E2E con token Supabase OK
