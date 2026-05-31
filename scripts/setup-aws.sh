#!/usr/bin/env bash
# ChessQuery — Fase B: provisiona la infra base en AWS (ECR, ECS cluster,
# Cloud Map, RDS + 6 DBs, Secrets Manager) de forma IDEMPOTENTE.
#
# Re-ejecutable: cada paso detecta si el recurso ya existe y lo saltea, así
# que si las credenciales de Academy caducan a mitad de camino, volvés a
# correrlo y retoma donde quedó.
#
# Pre-requisitos:
#   - aws CLI autenticado (aws sts get-caller-identity OK)
#   - psql, jq instalados
#   - Variables Supabase exportadas en el shell:
#       export SUPABASE_URL=https://<ref>.supabase.co
#       export SUPABASE_SERVICE_KEY=<service_role key>
#       export SUPABASE_JWT_SECRET=<Legacy JWT secret>
#
# Uso:  bash scripts/setup-aws.sh
set -euo pipefail

# ─── Config ──────────────────────────────────────────────────────────────────
export AWS_REGION="${AWS_REGION:-us-east-1}"
export PROJECT="${PROJECT:-chessquery}"
AWS_ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
TASK_EXECUTION_ROLE_ARN="arn:aws:iam::${AWS_ACCOUNT_ID}:role/LabRole"
ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
OUT="infrastructure/aws/.deploy-outputs.env"   # gitignored — ARNs y endpoints

# Variables Supabase obligatorias
: "${SUPABASE_URL:?Exporta SUPABASE_URL antes de correr}"
: "${SUPABASE_SERVICE_KEY:?Exporta SUPABASE_SERVICE_KEY antes de correr}"
: "${SUPABASE_JWT_SECRET:?Exporta SUPABASE_JWT_SECRET antes de correr}"

SERVICES=(api-gateway ms-users ms-tournament ms-game ms-notifications ms-analytics ms-etl bff-player bff-organizer)
DATABASES=(users_db tournament_db game_db notifications_db analytics_db etl_db)

step(){ echo -e "\n\033[1;36m▶ $*\033[0m"; }
ok(){ echo -e "  \033[0;32m✓ $*\033[0m"; }

mkdir -p "$(dirname "$OUT")"
echo "AWS_REGION=$AWS_REGION"                 >  "$OUT"
echo "AWS_ACCOUNT_ID=$AWS_ACCOUNT_ID"         >> "$OUT"
echo "ECR_REGISTRY=$ECR_REGISTRY"             >> "$OUT"
echo "TASK_EXECUTION_ROLE_ARN=$TASK_EXECUTION_ROLE_ARN" >> "$OUT"

# ─── 1. ECR (un repo por servicio) ──────────────────────────────────────────
step "1/6 Repositorios ECR"
for svc in "${SERVICES[@]}"; do
  if aws ecr describe-repositories --repository-names "$PROJECT/$svc" --region "$AWS_REGION" >/dev/null 2>&1; then
    ok "$svc (ya existe)"
  else
    aws ecr create-repository --repository-name "$PROJECT/$svc" --region "$AWS_REGION" \
      --image-scanning-configuration scanOnPush=true --image-tag-mutability MUTABLE >/dev/null
    ok "$svc (creado)"
  fi
done

# ─── 2. Cluster ECS ──────────────────────────────────────────────────────────
step "2/6 Cluster ECS Fargate"
if [ "$(aws ecs describe-clusters --clusters "$PROJECT-cluster" --query 'clusters[0].status' --output text 2>/dev/null)" = "ACTIVE" ]; then
  ok "$PROJECT-cluster (ya activo)"
else
  aws ecs create-cluster --cluster-name "$PROJECT-cluster" \
    --capacity-providers FARGATE FARGATE_SPOT \
    --default-capacity-provider-strategy capacityProvider=FARGATE_SPOT,weight=4 capacityProvider=FARGATE,weight=1 \
    --region "$AWS_REGION" >/dev/null
  ok "$PROJECT-cluster (creado)"
fi

# NOTA: AWS Academy bloquea Cloud Map (servicediscovery:Create*). Por eso NO se
# crea DNS interno: el despliegue usa una task ECS única con todos los contenedores
# comunicándose por localhost (ver infrastructure/aws/task-definitions/chessquery-stack.template.json).
VPC_ID="$(aws ec2 describe-vpcs --filters Name=isDefault,Values=true --query 'Vpcs[0].VpcId' --output text)"
echo "VPC_ID=$VPC_ID"          >> "$OUT"
echo "RABBITMQ_HOST=localhost" >> "$OUT"

# ─── 3. RDS PostgreSQL + 6 bases ─────────────────────────────────────────────
step "3/5 RDS PostgreSQL"
# Security group RDS
SG_RDS="$(aws ec2 describe-security-groups --filters Name=group-name,Values=$PROJECT-rds-sg --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || true)"
if [ -z "$SG_RDS" ] || [ "$SG_RDS" = "None" ]; then
  SG_RDS=$(aws ec2 create-security-group --group-name "$PROJECT-rds-sg" --description "RDS ChessQuery" --vpc-id "$VPC_ID" --query 'GroupId' --output text)
  ok "SG RDS creado ($SG_RDS)"
else
  ok "SG RDS ya existe ($SG_RDS)"
fi
# Permitir tráfico interno de la VPC (la task Fargate) + tu IP pública (psql).
# Usamos el CIDR REAL de la VPC default (suele ser 172.31.0.0/16, no 10/8).
VPC_CIDR="$(aws ec2 describe-vpcs --vpc-ids "$VPC_ID" --query 'Vpcs[0].CidrBlock' --output text)"
MY_IP="$(curl -fsSL https://checkip.amazonaws.com | tr -d '\n')"
aws ec2 authorize-security-group-ingress --group-id "$SG_RDS" --protocol tcp --port 5432 --cidr "$VPC_CIDR"    >/dev/null 2>&1 || true
aws ec2 authorize-security-group-ingress --group-id "$SG_RDS" --protocol tcp --port 5432 --cidr "${MY_IP}/32"  >/dev/null 2>&1 || true
ok "ingress 5432 abierto a VPC ($VPC_CIDR) + tu IP ($MY_IP)"

# Password en Secrets Manager (se genera una sola vez)
if aws secretsmanager describe-secret --secret-id "$PROJECT/db-password" >/dev/null 2>&1; then
  DB_PASSWORD="$(aws secretsmanager get-secret-value --secret-id "$PROJECT/db-password" --query SecretString --output text)"
  ok "db-password ya existe en Secrets Manager"
else
  DB_PASSWORD="$(openssl rand -base64 24 | tr -d '/+=')"
  aws secretsmanager create-secret --name "$PROJECT/db-password" --secret-string "$DB_PASSWORD" >/dev/null
  ok "db-password generado y guardado"
fi

# Instancia RDS
if aws rds describe-db-instances --db-instance-identifier "$PROJECT-pg" >/dev/null 2>&1; then
  ok "instancia $PROJECT-pg ya existe"
else
  aws rds create-db-instance \
    --db-instance-identifier "$PROJECT-pg" \
    --db-instance-class db.t4g.micro \
    --engine postgres --engine-version 16.8 \
    --master-username chessquery --master-user-password "$DB_PASSWORD" \
    --allocated-storage 20 --storage-type gp3 \
    --vpc-security-group-ids "$SG_RDS" \
    --backup-retention-period 1 --no-multi-az --publicly-accessible \
    --region "$AWS_REGION" >/dev/null
  ok "instancia $PROJECT-pg en creación"
fi
echo "  esperando que RDS esté disponible (puede tardar ~8 min)…"
aws rds wait db-instance-available --db-instance-identifier "$PROJECT-pg"
DB_HOST="$(aws rds describe-db-instances --db-instance-identifier "$PROJECT-pg" --query 'DBInstances[0].Endpoint.Address' --output text)"
ok "RDS disponible: $DB_HOST"

# Crear las 6 bases (idempotente)
step "    creando las 6 bases en RDS"
for db in "${DATABASES[@]}"; do
  exists=$(PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -U chessquery -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$db'" 2>/dev/null || echo "")
  if [ "$exists" = "1" ]; then
    ok "$db (ya existe)"
  else
    PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -U chessquery -d postgres -c "CREATE DATABASE $db;" >/dev/null
    ok "$db (creada)"
  fi
done
echo "SG_RDS=$SG_RDS"   >> "$OUT"
echo "DB_HOST=$DB_HOST" >> "$OUT"

# ─── 4. Secrets Manager (resto) ──────────────────────────────────────────────
step "4/5 Secrets Manager"
upsert_secret(){ # nombre  valor
  local name="$1" val="$2"
  if aws secretsmanager describe-secret --secret-id "$name" >/dev/null 2>&1; then
    aws secretsmanager put-secret-value --secret-id "$name" --secret-string "$val" >/dev/null
    ok "$name (actualizado)"
  else
    aws secretsmanager create-secret --name "$name" --secret-string "$val" >/dev/null
    ok "$name (creado)"
  fi
}
RABBITMQ_PASSWORD="$(aws secretsmanager get-secret-value --secret-id "$PROJECT/rabbitmq-password" --query SecretString --output text 2>/dev/null || openssl rand -base64 24 | tr -d '/+=')"
WEBHOOK_SECRET="$(aws secretsmanager get-secret-value --secret-id "$PROJECT/supabase-webhook-secret" --query SecretString --output text 2>/dev/null || openssl rand -base64 32)"
upsert_secret "$PROJECT/rabbitmq-password"       "$RABBITMQ_PASSWORD"
upsert_secret "$PROJECT/supabase-service-key"    "$SUPABASE_SERVICE_KEY"
upsert_secret "$PROJECT/jwt-secret"              "$SUPABASE_JWT_SECRET"
upsert_secret "$PROJECT/supabase-webhook-secret" "$WEBHOOK_SECRET"

# Resolver ARNs
DB_PASSWORD_ARN=$(aws secretsmanager describe-secret --secret-id "$PROJECT/db-password" --query ARN --output text)
RABBITMQ_PASSWORD_ARN=$(aws secretsmanager describe-secret --secret-id "$PROJECT/rabbitmq-password" --query ARN --output text)
SUPABASE_SERVICE_KEY_ARN=$(aws secretsmanager describe-secret --secret-id "$PROJECT/supabase-service-key" --query ARN --output text)
JWT_SECRET_ARN=$(aws secretsmanager describe-secret --secret-id "$PROJECT/jwt-secret" --query ARN --output text)
SUPABASE_WEBHOOK_SECRET_ARN=$(aws secretsmanager describe-secret --secret-id "$PROJECT/supabase-webhook-secret" --query ARN --output text)
{
  echo "SUPABASE_URL=$SUPABASE_URL"
  echo "DB_PASSWORD_ARN=$DB_PASSWORD_ARN"
  echo "RABBITMQ_PASSWORD_ARN=$RABBITMQ_PASSWORD_ARN"
  echo "SUPABASE_SERVICE_KEY_ARN=$SUPABASE_SERVICE_KEY_ARN"
  echo "JWT_SECRET_ARN=$JWT_SECRET_ARN"
  echo "SUPABASE_WEBHOOK_SECRET_ARN=$SUPABASE_WEBHOOK_SECRET_ARN"
} >> "$OUT"

# ─── 5. Resumen ──────────────────────────────────────────────────────────────
step "5/5 Listo — infra base provisionada"
echo "  Salidas guardadas en: $OUT"
echo "  Cargá los GitHub Secrets con:  bash scripts/set-gh-secrets.sh"
