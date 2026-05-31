#!/usr/bin/env bash
# ChessQuery — Fase C: carga los GitHub Secrets que consumen build-and-push.yml
# y deploy.yml, leyendo las salidas de setup-aws.sh.
#
# Pre-requisitos:
#   - gh CLI autenticado (gh auth status)
#   - scripts/setup-aws.sh ya corrido → infrastructure/aws/.deploy-outputs.env existe
#   - Credenciales AWS Academy a mano (access key, secret, session token)
#
# Uso:  bash scripts/set-gh-secrets.sh
set -euo pipefail

OUT="infrastructure/aws/.deploy-outputs.env"
[ -f "$OUT" ] || { echo "❌ No existe $OUT — corré primero scripts/setup-aws.sh"; exit 1; }
# shellcheck disable=SC1090
source "$OUT"

echo "▶ Credenciales AWS Academy (caducan cada 4h — pegalas del lab)"
read -rp "  AWS_ACCESS_KEY_ID: " AKID
read -rsp "  AWS_SECRET_ACCESS_KEY: " ASAK; echo
read -rsp "  AWS_SESSION_TOKEN: " ASTK; echo

set_secret(){ printf '%s' "$2" | gh secret set "$1" --body -; echo "  ✓ $1"; }

echo "▶ Cargando GitHub Secrets…"
# Credenciales (rotan)
set_secret AWS_ACCESS_KEY_ID      "$AKID"
set_secret AWS_SECRET_ACCESS_KEY  "$ASAK"
set_secret AWS_SESSION_TOKEN      "$ASTK"
# Estables (de las salidas de setup-aws.sh)
set_secret AWS_REGION                 "$AWS_REGION"
set_secret AWS_ACCOUNT_ID             "$AWS_ACCOUNT_ID"
set_secret TASK_EXECUTION_ROLE_ARN    "$TASK_EXECUTION_ROLE_ARN"
set_secret DB_HOST                    "$DB_HOST"
set_secret RABBITMQ_HOST              "$RABBITMQ_HOST"
set_secret SUPABASE_URL               "$SUPABASE_URL"
set_secret DB_PASSWORD_ARN            "$DB_PASSWORD_ARN"
set_secret RABBITMQ_PASSWORD_ARN      "$RABBITMQ_PASSWORD_ARN"
set_secret SUPABASE_SERVICE_KEY_ARN   "$SUPABASE_SERVICE_KEY_ARN"
set_secret JWT_SECRET_ARN             "$JWT_SECRET_ARN"
set_secret SUPABASE_WEBHOOK_SECRET_ARN "$SUPABASE_WEBHOOK_SECRET_ARN"

echo "✓ Listo. Verificá con: gh secret list"
