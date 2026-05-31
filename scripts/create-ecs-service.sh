#!/usr/bin/env bash
# ChessQuery — Fase D (primera vez): registra la task-def consolidada y crea
# el ÚNICO service ECS (chessquery-stack) con IP pública, exponiendo solo 8080.
# Idempotente: si el service ya existe, solo actualiza la task-def.
#
# Pre-requisitos:
#   - scripts/setup-aws.sh ya corrido (infrastructure/aws/.deploy-outputs.env existe)
#   - Imágenes ya subidas a ECR (workflow build-and-push.yml ejecutado)
#   - Variables exportadas en el shell:
#       export SUPABASE_URL=https://<ref>.supabase.co
#       export IMAGE_TAG=v0.1.0     # el tag que pusiste en build-and-push
#
# Uso:  bash scripts/create-ecs-service.sh
set -euo pipefail

OUT="infrastructure/aws/.deploy-outputs.env"
[ -f "$OUT" ] || { echo "❌ Falta $OUT — corré primero scripts/setup-aws.sh"; exit 1; }
# shellcheck disable=SC1090
source "$OUT"
: "${IMAGE_TAG:?Exporta IMAGE_TAG (ej: v0.1.0)}"
: "${SUPABASE_URL:?Exporta SUPABASE_URL}"

PROJECT="${PROJECT:-chessquery}"
CLUSTER="$PROJECT-cluster"
SERVICE="chessquery-stack"
step(){ echo -e "\n\033[1;36m▶ $*\033[0m"; }
ok(){ echo -e "  \033[0;32m✓ $*\033[0m"; }

# ─── 1. Render + registro de la task definition ──────────────────────────────
step "1/3 Render + registro de task-def ($SERVICE)"
export ECR_REGISTRY IMAGE_TAG AWS_REGION TASK_EXECUTION_ROLE_ARN DB_HOST SUPABASE_URL \
       DB_PASSWORD_ARN RABBITMQ_PASSWORD_ARN SUPABASE_SERVICE_KEY_ARN JWT_SECRET_ARN \
       SUPABASE_WEBHOOK_SECRET_ARN
rendered="/tmp/task-def-$SERVICE.json"
envsubst < infrastructure/aws/task-definitions/chessquery-stack.template.json > "$rendered"
TASK_DEF_ARN=$(aws ecs register-task-definition --cli-input-json "file://$rendered" \
  --query 'taskDefinition.taskDefinitionArn' --output text)
ok "registrada: $TASK_DEF_ARN"

# ─── 2. Security group (solo 8080 público) ───────────────────────────────────
step "2/3 Security group"
SG_ECS="$(aws ec2 describe-security-groups --filters Name=group-name,Values=$PROJECT-ecs-sg --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || true)"
if [ -z "$SG_ECS" ] || [ "$SG_ECS" = "None" ]; then
  SG_ECS=$(aws ec2 create-security-group --group-name "$PROJECT-ecs-sg" --description "ECS chessquery-stack" --vpc-id "$VPC_ID" --query 'GroupId' --output text)
  ok "SG creado ($SG_ECS)"
else
  ok "SG ya existe ($SG_ECS)"
fi
# Solo el api-gateway (8080) queda público; el resto es localhost dentro de la task
aws ec2 authorize-security-group-ingress --group-id "$SG_ECS" --protocol tcp --port 8080 --cidr 0.0.0.0/0 >/dev/null 2>&1 || true
ok "ingress 8080 abierto a internet"
SUBNETS="$(aws ec2 describe-subnets --filters Name=vpc-id,Values=$VPC_ID Name=default-for-az,Values=true --query 'Subnets[].SubnetId' --output text | tr '\t' ',')"
[ -z "$SUBNETS" ] && SUBNETS="$(aws ec2 describe-subnets --filters Name=vpc-id,Values=$VPC_ID --query 'Subnets[].SubnetId' --output text | tr '\t' ',')"
ok "subnets: $SUBNETS"

# ─── 3. Crear o actualizar el service ────────────────────────────────────────
step "3/3 Service ECS"
STATUS="$(aws ecs describe-services --cluster "$CLUSTER" --services "$SERVICE" --query 'services[0].status' --output text 2>/dev/null || echo None)"
if [ "$STATUS" = "ACTIVE" ]; then
  aws ecs update-service --cluster "$CLUSTER" --service "$SERVICE" \
    --task-definition "$TASK_DEF_ARN" --force-new-deployment >/dev/null
  ok "service ya existía → forzado nuevo deployment"
else
  aws ecs create-service \
    --cluster "$CLUSTER" --service-name "$SERVICE" \
    --task-definition "$TASK_DEF_ARN" \
    --desired-count 1 --launch-type FARGATE \
    --network-configuration "awsvpcConfiguration={subnets=[$SUBNETS],securityGroups=[$SG_ECS],assignPublicIp=ENABLED}" >/dev/null
  ok "service $SERVICE creado"
fi

echo "SG_ECS=$SG_ECS" >> "$OUT"
echo -e "\n  Seguimiento:  aws ecs describe-services --cluster $CLUSTER --services $SERVICE --query 'services[0].{Running:runningCount,Desired:desiredCount}'"
echo "  IP pública:   ver RUNBOOK_ECS §4.4 (list-tasks → describe-tasks → ENI → PublicIp)"
