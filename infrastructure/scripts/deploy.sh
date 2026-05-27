#!/usr/bin/env bash
# Despliega ChessQuery a Kubernetes.
#   ./deploy.sh --env local    (default)
#   ./deploy.sh --env aws      (requiere ECR_REGISTRY exportado)
set -euo pipefail

ENV="local"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --env) ENV="$2"; shift 2 ;;
    *)     echo "Argumento desconocido: $1"; exit 1 ;;
  esac
done

K8S_DIR="$(cd "$(dirname "$0")/../k8s" && pwd)"
NS="chessquery"

if [[ "${ENV}" == "aws" ]]; then
  : "${ECR_REGISTRY:?Para --env aws hay que exportar ECR_REGISTRY (ej. 1234.dkr.ecr.us-east-1.amazonaws.com)}"
  RENDER() { envsubst < "$1"; }
else
  # En local, las imágenes se cargan vía `docker tag` y k3s las usa con
  # imagePullPolicy: IfNotPresent. Sustituimos ${ECR_REGISTRY} por "local"
  # para que el YAML sea válido aunque la imagen luego no se pueda pull.
  export ECR_REGISTRY="local"
  RENDER() { envsubst < "$1"; }
fi

apply() {
  echo "▶ kubectl apply -f $(basename "$1")"
  RENDER "$1" | kubectl apply -f -
}

# 1. Namespace
apply "${K8S_DIR}/namespace.yaml"

# 2. ConfigMap + Secret + StorageClass
apply "${K8S_DIR}/configmaps/chessquery-config.yaml"
apply "${K8S_DIR}/secrets/chessquery-secrets.yaml"
apply "${K8S_DIR}/storageclass/local-storage.yaml"

# 3. Infra (RabbitMQ + Redis)
apply "${K8S_DIR}/deployments/rabbitmq-deployment.yaml"
apply "${K8S_DIR}/deployments/redis-deployment.yaml"

# 4. Microservicios
apply "${K8S_DIR}/deployments/java-services.yaml"
apply "${K8S_DIR}/deployments/bff-services.yaml"

# 5. Ingress + HPA
apply "${K8S_DIR}/ingress/chessquery-ingress.yaml"
apply "${K8S_DIR}/hpa/hpa.yaml"

# 6. Esperar rollouts
DEPLOYMENTS=(
  rabbitmq redis
  ms-users ms-tournament ms-game ms-analytics ms-notifications api-gateway
  ms-etl bff-player bff-organizer bff-admin frontend
)
for d in "${DEPLOYMENTS[@]}"; do
  echo "▶ rollout status ${d}"
  kubectl -n "${NS}" rollout status "deployment/${d}" --timeout=240s || true
done

echo
echo "✅ Estado del cluster:"
kubectl -n "${NS}" get pods
echo
if [[ "${ENV}" == "local" ]]; then
  echo "🌐 URL local:  http://chessquery.local/  (asegúrate de tener la entrada en /etc/hosts)"
else
  echo "🌐 Consigue la IP del Ingress: kubectl -n ingress-nginx get svc"
fi
