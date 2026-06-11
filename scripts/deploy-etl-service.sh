#!/usr/bin/env bash
# ChessQuery — Despliega ms-etl como SEGUNDO service ECS (chessquery-etl) con
# Application Auto Scaling. La task principal (chessquery-stack) ya usa los 10
# contenedores que permite Academy, así que ms-etl va en una task propia (T3,
# opción B del ROADMAP_V1 — se mantiene RabbitMQ, sin Amazon MQ).
#
# Conectividad (Academy bloquea Cloud Map/Service Discovery):
#   ms-etl necesita RabbitMQ (5672), Redis (6379) y ms-users (8081), que viven
#   DENTRO de la task principal. Este script resuelve la IP privada de esa task
#   al momento del deploy y la hornea en la task-def de ms-etl.
#   ⚠️  Si la task principal se redespliega (cambia de IP), HAY QUE RE-CORRER
#       este script (tarda ~1 min; ver docs/DESPLIEGUE_ETL.md).
#
# Pre-requisitos:
#   - scripts/setup-aws.sh corrido (.deploy-outputs.env existe)
#   - Imagen ms-etl en ECR (scripts/build-push-ecr.sh con el mismo IMAGE_TAG)
#   - chessquery-stack corriendo (de su task sale la IP de RabbitMQ/Redis/ms-users)
#   - export IMAGE_TAG=v0.3.1
#
# Uso:  bash scripts/deploy-etl-service.sh
#       ETL_ADMIN_CIDR=$(curl -s https://checkip.amazonaws.com)/32 bash scripts/deploy-etl-service.sh
#       (ETL_ADMIN_CIDR opcional: abre 8086 a tu IP para disparar syncs con curl)
set -euo pipefail

OUT="infrastructure/aws/.deploy-outputs.env"
[ -f "$OUT" ] || { echo "❌ Falta $OUT — corré primero scripts/setup-aws.sh"; exit 1; }
# shellcheck disable=SC1090
source "$OUT"
: "${IMAGE_TAG:?Exporta IMAGE_TAG (ej: v0.3.1)}"

PROJECT="${PROJECT:-chessquery}"
CLUSTER="$PROJECT-cluster"
STACK_SERVICE="chessquery-stack"
ETL_SERVICE="chessquery-etl"
step(){ echo -e "\n\033[1;36m▶ $*\033[0m"; }
ok(){ echo -e "  \033[0;32m✓ $*\033[0m"; }

# ─── 1. IP privada de la task principal (RabbitMQ/Redis/ms-users viven ahí) ──
step "1/5 Resolver IP privada de $STACK_SERVICE"
STACK_TASK_ARN=$(aws ecs list-tasks --cluster "$CLUSTER" --service-name "$STACK_SERVICE" \
  --desired-status RUNNING --query 'taskArns[0]' --output text)
[ "$STACK_TASK_ARN" = "None" ] || [ -z "$STACK_TASK_ARN" ] && {
  echo "❌ $STACK_SERVICE no tiene tasks RUNNING — prendé el stack primero"; exit 1; }
STACK_PRIVATE_IP=$(aws ecs describe-tasks --cluster "$CLUSTER" --tasks "$STACK_TASK_ARN" \
  --query "tasks[0].attachments[0].details[?name=='privateIPv4Address'].value | [0]" --output text)
export STACK_PRIVATE_IP
ok "stack en $STACK_PRIVATE_IP (si el stack se redespliega, re-correr este script)"

# ─── 2. Security group: ETL → stack (5672/6379/8081, regla self) ─────────────
step "2/5 Security group"
SG_ECS="${SG_ECS:-$(aws ec2 describe-security-groups --filters Name=group-name,Values=$PROJECT-ecs-sg --query 'SecurityGroups[0].GroupId' --output text)}"
# Ambos services usan el mismo SG; la regla self-referenciada habilita el
# tráfico ETL→stack sin abrir nada a internet.
for port in 5672 6379 8081; do
  aws ec2 authorize-security-group-ingress --group-id "$SG_ECS" \
    --protocol tcp --port "$port" --source-group "$SG_ECS" >/dev/null 2>&1 || true
done
ok "ingress interno 5672/6379/8081 (self) en $SG_ECS"
if [ -n "${ETL_ADMIN_CIDR:-}" ]; then
  aws ec2 authorize-security-group-ingress --group-id "$SG_ECS" \
    --protocol tcp --port 8086 --cidr "$ETL_ADMIN_CIDR" >/dev/null 2>&1 || true
  ok "ingress 8086 abierto a $ETL_ADMIN_CIDR (triggers manuales de sync)"
fi
SUBNETS="$(aws ec2 describe-subnets --filters Name=vpc-id,Values=$VPC_ID Name=default-for-az,Values=true --query 'Subnets[].SubnetId' --output text | tr '\t' ',')"
[ -z "$SUBNETS" ] && SUBNETS="$(aws ec2 describe-subnets --filters Name=vpc-id,Values=$VPC_ID --query 'Subnets[].SubnetId' --output text | tr '\t' ',')"

# ─── 3. Render + registro de la task definition ──────────────────────────────
step "3/5 Task definition ($ETL_SERVICE)"
export ECR_REGISTRY IMAGE_TAG AWS_REGION TASK_EXECUTION_ROLE_ARN DB_HOST \
       DB_PASSWORD_ARN RABBITMQ_PASSWORD_ARN
rendered="/tmp/task-def-$ETL_SERVICE.json"
envsubst < infrastructure/aws/task-definitions/ms-etl.template.json > "$rendered"
TASK_DEF_ARN=$(aws ecs register-task-definition --cli-input-json "file://$rendered" \
  --query 'taskDefinition.taskDefinitionArn' --output text)
ok "registrada: $TASK_DEF_ARN"

# ─── 4. Crear o actualizar el service ────────────────────────────────────────
step "4/5 Service ECS"
STATUS="$(aws ecs describe-services --cluster "$CLUSTER" --services "$ETL_SERVICE" --query 'services[0].status' --output text 2>/dev/null || echo None)"
if [ "$STATUS" = "ACTIVE" ]; then
  aws ecs update-service --cluster "$CLUSTER" --service "$ETL_SERVICE" \
    --task-definition "$TASK_DEF_ARN" --force-new-deployment >/dev/null
  ok "service ya existía → forzado nuevo deployment"
else
  aws ecs create-service \
    --cluster "$CLUSTER" --service-name "$ETL_SERVICE" \
    --task-definition "$TASK_DEF_ARN" \
    --desired-count 1 --launch-type FARGATE \
    --network-configuration "awsvpcConfiguration={subnets=[$SUBNETS],securityGroups=[$SG_ECS],assignPublicIp=ENABLED}" >/dev/null
  ok "service $ETL_SERVICE creado"
fi

# ─── 5. Auto Scaling (target tracking por CPU) ───────────────────────────────
# 1–2 tasks según CPU promedio del service: una sincronización completa
# (AJEFECH/Lichess/Chess.com) es CPU-bound; con CPU > 70% por 2 evaluaciones
# se agrega la segunda task, y al enfriarse se vuelve a 1 (ahorra créditos
# Academy). Los syncs son disparos puntuales e idempotentes por fuente, así
# que dos réplicas no duplican corridas (cada POST cae en una sola task).
step "5/5 Application Auto Scaling"
aws application-autoscaling register-scalable-target \
  --service-namespace ecs \
  --resource-id "service/$CLUSTER/$ETL_SERVICE" \
  --scalable-dimension ecs:service:DesiredCount \
  --min-capacity 1 --max-capacity 2 >/dev/null
aws application-autoscaling put-scaling-policy \
  --service-namespace ecs \
  --resource-id "service/$CLUSTER/$ETL_SERVICE" \
  --scalable-dimension ecs:service:DesiredCount \
  --policy-name "$ETL_SERVICE-cpu70" \
  --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration '{
    "TargetValue": 70.0,
    "PredefinedMetricSpecification": { "PredefinedMetricType": "ECSServiceAverageCPUUtilization" },
    "ScaleOutCooldown": 120,
    "ScaleInCooldown": 300
  }' >/dev/null
ok "autoscaling 1–2 tasks, target CPU 70% (out 120s / in 300s)"

echo -e "\n  Seguimiento: aws ecs describe-services --cluster $CLUSTER --services $ETL_SERVICE --query 'services[0].{Running:runningCount,Desired:desiredCount}'"
echo "  Logs:        aws logs tail /ecs/chessquery/ms-etl --follow"
echo "  Sync manual: curl -X POST http://<IP-publica-task-etl>:8086/etl/sync/chesscom   (requiere ETL_ADMIN_CIDR)"
echo "  Estado:      curl http://<IP-publica-task-etl>:8086/etl/status"
