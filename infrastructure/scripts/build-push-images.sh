#!/usr/bin/env bash
# Construye y pushea todas las imágenes ChessQuery a ECR.
#   ./build-push-images.sh [TAG]   (TAG por defecto: latest)
# Requiere: docker, aws CLI, AWS_ACCOUNT_ID, AWS_REGION.
set -euo pipefail

: "${AWS_ACCOUNT_ID:?Falta AWS_ACCOUNT_ID}"
: "${AWS_REGION:?Falta AWS_REGION}"
TAG="${1:-latest}"

ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

# (servicio, ruta-del-Dockerfile relativa al root del repo)
SERVICES=(
  "ms-users:infrastructure/docker/ms-users/Dockerfile"
  "ms-tournament:infrastructure/docker/ms-tournament/Dockerfile"
  "ms-game:infrastructure/docker/ms-game/Dockerfile"
  "ms-analytics:infrastructure/docker/ms-analytics/Dockerfile"
  "ms-notifications:infrastructure/docker/ms-notifications/Dockerfile"
  "api-gateway:infrastructure/docker/api-gateway/Dockerfile"
  "ms-etl:infrastructure/docker/ms-etl/Dockerfile"
  "bff-player:infrastructure/docker/bff-player/Dockerfile"
  "bff-organizer:infrastructure/docker/bff-organizer/Dockerfile"
  "bff-admin:infrastructure/docker/bff-admin/Dockerfile"
  "frontend:infrastructure/docker/frontend/Dockerfile"
)

# Posicionarse en el root del repo (este script vive en infrastructure/scripts)
ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "${ROOT_DIR}"

echo "▶ Login a ECR (${ECR_REGISTRY})..."
aws ecr get-login-password --region "${AWS_REGION}" \
  | docker login --username AWS --password-stdin "${ECR_REGISTRY}"

PUSHED=()
for entry in "${SERVICES[@]}"; do
  svc="${entry%%:*}"
  dockerfile="${entry#*:}"
  image="${ECR_REGISTRY}/chessquery/${svc}:${TAG}"

  echo
  echo "─── ${svc} (${dockerfile} → ${image})"
  docker build -f "${dockerfile}" -t "${image}" .
  docker push "${image}"
  PUSHED+=("${image}")
done

echo
echo "✅ Imágenes pusheadas:"
for img in "${PUSHED[@]}"; do
  echo "   - ${img}"
done
