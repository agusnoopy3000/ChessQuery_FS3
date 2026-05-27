#!/usr/bin/env bash
# ChessQuery — bootstrap de k3s para demo local (Linux).
# Instala k3s SIN traefik, monta Nginx Ingress y cert-manager.
set -euo pipefail

INGRESS_VERSION="${INGRESS_VERSION:-v1.11.2}"
CERTMGR_VERSION="${CERTMGR_VERSION:-v1.15.3}"

echo "▶ Instalando k3s (sin traefik)..."
curl -sfL https://get.k3s.io | sh -s - --disable=traefik --write-kubeconfig-mode=644

echo "▶ Esperando a que k3s esté listo..."
sudo k3s kubectl wait --for=condition=Ready node --all --timeout=120s

KUBECONFIG_PATH="/etc/rancher/k3s/k3s.yaml"
echo "▶ KUBECONFIG en: ${KUBECONFIG_PATH}"
echo "  Exporta así:  export KUBECONFIG=${KUBECONFIG_PATH}"

export KUBECONFIG="${KUBECONFIG_PATH}"

echo "▶ Instalando Nginx Ingress Controller ${INGRESS_VERSION}..."
kubectl apply -f "https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-${INGRESS_VERSION}/deploy/static/provider/baremetal/deploy.yaml"
kubectl -n ingress-nginx wait --for=condition=Available deployment/ingress-nginx-controller --timeout=180s || true

echo "▶ Instalando cert-manager ${CERTMGR_VERSION}..."
kubectl apply -f "https://github.com/cert-manager/cert-manager/releases/download/${CERTMGR_VERSION}/cert-manager.yaml"
kubectl -n cert-manager wait --for=condition=Available deployment/cert-manager --timeout=180s || true

echo "▶ Registrando chessquery.local en /etc/hosts..."
if ! grep -q "chessquery.local" /etc/hosts; then
  echo "127.0.0.1 chessquery.local" | sudo tee -a /etc/hosts
fi

echo "✅ k3s listo. Verifica con: kubectl get nodes && kubectl get pods -A"
