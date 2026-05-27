#!/usr/bin/env bash
# Crea los repositorios ECR para todas las imágenes de ChessQuery.
# Requiere: AWS CLI configurado, AWS_ACCOUNT_ID y AWS_REGION exportados.
set -euo pipefail

: "${AWS_ACCOUNT_ID:?Falta AWS_ACCOUNT_ID}"
: "${AWS_REGION:?Falta AWS_REGION}"

SERVICES=(
  ms-users ms-tournament ms-game ms-analytics ms-notifications
  api-gateway ms-etl
  bff-player bff-organizer bff-admin
  frontend
)

ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
echo "▶ ECR Registry: ${ECR_REGISTRY}"

for svc in "${SERVICES[@]}"; do
  repo="chessquery/${svc}"
  echo "▶ Creando repo: ${repo}"
  aws ecr describe-repositories --repository-names "${repo}" --region "${AWS_REGION}" >/dev/null 2>&1 \
    || aws ecr create-repository \
        --repository-name "${repo}" \
        --region "${AWS_REGION}" \
        --image-scanning-configuration scanOnPush=true \
        --image-tag-mutability MUTABLE \
        >/dev/null
done

echo
echo "✅ Repositorios ECR creados/verificados."
echo "   Base URI: ${ECR_REGISTRY}/chessquery/<servicio>"
echo
echo "Exporta:"
echo "   export ECR_REGISTRY=${ECR_REGISTRY}"
