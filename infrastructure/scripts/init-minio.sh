#!/bin/sh
# =============================================================================
# init-minio.sh
# Crea el bucket S3 en MinIO usando el cliente mc.
#
# Compatibilidad con AWS S3:
#   Los microservicios usan el SDK estándar de AWS/S3 con:
#     - endpoint:   http://minio:9000  (solo en local; vacío apunta a AWS)
#     - region:     us-east-1
#     - pathStyle:  true (requerido por MinIO; ignorado por AWS real)
#     - accessKey:  S3_ACCESS_KEY
#     - secretKey:  S3_SECRET_KEY
#     - bucket:     S3_BUCKET
#
#   En producción: eliminar S3_ENDPOINT_URL del .env y usar credenciales IAM.
#
# Variables de entorno esperadas:
#   S3_ACCESS_KEY  – access key (default: minioadmin)
#   S3_SECRET_KEY  – secret key (default: minioadmin)
#   S3_ENDPOINT    – endpoint MinIO (default: http://minio:9000)
#   S3_BUCKET      – nombre del bucket (default: chessquery-pgn)
# =============================================================================

set -e

ACCESS_KEY="${S3_ACCESS_KEY:-minioadmin}"
SECRET_KEY="${S3_SECRET_KEY:-minioadmin}"
ENDPOINT="${S3_ENDPOINT:-http://minio:9000}"
BUCKET="${S3_BUCKET:-chessquery-pgn}"
ALIAS="chessquery_local"

echo "[init-minio] Configurando alias '${ALIAS}' -> ${ENDPOINT}..."
mc alias set "${ALIAS}" "${ENDPOINT}" "${ACCESS_KEY}" "${SECRET_KEY}"

echo "[init-minio] Verificando bucket '${BUCKET}'..."
if mc ls "${ALIAS}/${BUCKET}" > /dev/null 2>&1; then
  echo "[init-minio] El bucket '${BUCKET}' ya existe. Sin cambios."
else
  mc mb "${ALIAS}/${BUCKET}"
  echo "[init-minio] Bucket '${BUCKET}' creado."
fi

# Política de acceso: privada por defecto (igual que S3).
# Los microservicios deben usar URLs pre-firmadas para exponer objetos.
mc anonymous set none "${ALIAS}/${BUCKET}"
echo "[init-minio] Política de acceso: privada (anonymous=none)."

echo "[init-minio] Setup completado."