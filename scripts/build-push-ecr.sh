#!/usr/bin/env bash
# ChessQuery — Build & push de las 8 imágenes a ECR DESDE LOCAL.
# Alternativa al workflow build-and-push.yml (bloqueado: la cuenta GitHub tiene
# los runners GitHub-hosted deshabilitados por billing).
#
# Pre-requisitos:
#   - docker corriendo, aws CLI autenticado
#   - scripts/setup-aws.sh ya corrido (repos ECR creados)
#   - export IMAGE_TAG=v0.1.0   (opcional, default v0.1.0)
#
# Uso:  bash scripts/build-push-ecr.sh
set -euo pipefail

OUT="infrastructure/aws/.deploy-outputs.env"
[ -f "$OUT" ] || { echo "❌ Falta $OUT — corré primero scripts/setup-aws.sh"; exit 1; }
# shellcheck disable=SC1090
source "$OUT"
IMAGE_TAG="${IMAGE_TAG:-v0.1.0}"

# servicio:Dockerfile (contexto = raíz del repo)
SERVICES=(
  "api-gateway:infrastructure/docker/api-gateway/Dockerfile"
  "ms-users:infrastructure/docker/ms-users/Dockerfile"
  "ms-tournament:infrastructure/docker/ms-tournament/Dockerfile"
  "ms-game:infrastructure/docker/ms-game/Dockerfile"
  "ms-analytics:infrastructure/docker/ms-analytics/Dockerfile"
  "ms-notifications:infrastructure/docker/ms-notifications/Dockerfile"
  "bff-player:infrastructure/docker/bff-player/Dockerfile"
  "bff-organizer:infrastructure/docker/bff-organizer/Dockerfile"
)

echo "▶ Login a ECR ($ECR_REGISTRY)"
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$ECR_REGISTRY"

for entry in "${SERVICES[@]}"; do
  svc="${entry%%:*}"; dockerfile="${entry#*:}"
  img="$ECR_REGISTRY/chessquery/$svc"
  echo -e "\n\033[1;36m▶ build $svc\033[0m  ($dockerfile)"
  docker build -f "$dockerfile" -t "$img:$IMAGE_TAG" -t "$img:latest" .
  echo "  push $img:$IMAGE_TAG"
  docker push "$img:$IMAGE_TAG"
  docker push "$img:latest"
  echo -e "  \033[0;32m✓ $svc → $img:$IMAGE_TAG\033[0m"
done

echo -e "\n✓ 8 imágenes en ECR (tag $IMAGE_TAG). Siguiente: bash scripts/create-ecs-service.sh"
