# ChessQuery — Operación de la demo (cheat-sheet)

> Referencia rápida para el equipo: links, cómo acceder, cómo chequear estados,
> encender/apagar (ahorro), redeploy y CI/CD. Cuenta AWS Academy `672782205900`,
> región `us-east-1`. Para el detalle ver `DESPLIEGUE_AWS_REALIZADO.md`.

## 🔗 Links

| Qué | URL |
|---|---|
| **Front jugador** (chess-portal) | http://chessquery-chess-portal.s3-website-us-east-1.amazonaws.com |
| **Front organizador** (organizer-panel) | http://chessquery-organizer-panel.s3-website-us-east-1.amazonaws.com |
| **API gateway** (vía ALB, URL estable) | http://chessquery-alb-984810293.us-east-1.elb.amazonaws.com |
| Repo / PR de la release | https://github.com/agusnoopy3000/ChessQuery_FS3 · PR #18 |
| Supabase (Auth + Storage) | proyecto `pmtxxzscpactsgkijpul` |

## 🧱 Identificadores AWS

```
Cluster ECS     chessquery-cluster
Service         chessquery-stack         (task-def rev 7 = release v0.2.0)
RDS             chessquery-pg            (PostgreSQL 16, 6 DBs)
ECR registry    672782205900.dkr.ecr.us-east-1.amazonaws.com/chessquery/<svc>
ALB             chessquery-alb           (DNS arriba; no cambia al reencender)
Target group    chessquery-gw-tg
SG ALB          sg-071f18480ea23a5c4     SG ECS  sg-00d4ad8b2817ad4cc
Runner CI/CD    fedora-runner            (/opt/actions-runner, servicio systemd)
```

---

## ✅ Chequear estados

```bash
# Salud del gateway por el ALB (lo más rápido para saber si "está arriba")
curl http://chessquery-alb-984810293.us-east-1.elb.amazonaws.com/actuator/health/readiness
# → {"status":"UP"}

# Frontends (deben dar 200)
curl -s -o /dev/null -w "%{http_code}\n" http://chessquery-chess-portal.s3-website-us-east-1.amazonaws.com
curl -s -o /dev/null -w "%{http_code}\n" http://chessquery-organizer-panel.s3-website-us-east-1.amazonaws.com

# Estado del service ECS (running/desired + task-def activa)
aws ecs describe-services --cluster chessquery-cluster --services chessquery-stack \
  --query 'services[0].{running:runningCount,desired:desiredCount,taskDef:taskDefinition}' --output table

# Salud del target en el ALB (debe ser "healthy")
aws elbv2 describe-target-health \
  --target-group-arn arn:aws:elasticloadbalancing:us-east-1:672782205900:targetgroup/chessquery-gw-tg/9ad837066ce1f97c \
  --query 'TargetHealthDescriptions[].TargetHealth.State' --output text

# Estado de RDS (available / stopped / starting)
aws rds describe-db-instances --db-instance-identifier chessquery-pg \
  --query 'DBInstances[0].DBInstanceStatus' --output text

# Logs de un contenedor (CloudWatch) — ej. gateway o ms-tournament
aws logs tail /ecs/chessquery-stack --since 10m --follow   # ajustar al log group real si difiere
```

### Probar la API autenticando de verdad (token real de Supabase)
```bash
SUPA=https://pmtxxzscpactsgkijpul.supabase.co
ANON=<anon-key>     # mismo de frontend/apps/*/.env.production
ALB=http://chessquery-alb-984810293.us-east-1.elb.amazonaws.com
# login → token
TOKEN=$(curl -s -X POST "$SUPA/auth/v1/token?grant_type=password" -H "apikey: $ANON" \
  -H "Content-Type: application/json" -d '{"email":"<user>","password":"<pass>"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
# llamada protegida (rankings con datos reales)
curl -s -H "Authorization: Bearer $TOKEN" "$ALB/api/player/rankings" | head -c 300
```

---

## 🔌 Encender / apagar (ahorro de saldo Academy)

```bash
# APAGAR (no borra nada; el DNS del ALB se conserva)
aws ecs update-service --cluster chessquery-cluster --service chessquery-stack --desired-count 0
aws rds stop-db-instance --db-instance-identifier chessquery-pg

# ENCENDER (primero RDS, esperar ~2-4 min a "available", luego ECS)
aws rds start-db-instance --db-instance-identifier chessquery-pg
aws ecs update-service --cluster chessquery-cluster --service chessquery-stack --desired-count 1
```
> ⚠️ Las credenciales AWS Academy **expiran cada 4 h**: repegarlas del lab a
> `~/.aws/credentials` (incluye `aws_session_token`). Para CI/CD por Actions,
> además refrescar los GitHub Secrets: `bash scripts/set-gh-secrets.sh`.

---

## 🚀 Redeploy (cuando haya cambios nuevos)

```bash
# Backend (imágenes + service). Requiere docker + AWS CLI + creds frescas.
export IMAGE_TAG=v0.2.1
bash scripts/build-push-ecr.sh                      # build+push 8 imágenes a ECR
# render task-def + register + update-service (mantener CORS + load balancer):
export SUPABASE_URL=https://pmtxxzscpactsgkijpul.supabase.co
export GATEWAY_CORS_ALLOWED_ORIGINS="http://chessquery-chess-portal.s3-website-us-east-1.amazonaws.com,http://chessquery-organizer-panel.s3-website-us-east-1.amazonaws.com"
# (ver el procedimiento completo en DESPLIEGUE_AWS_REALIZADO.md §7)

# Frontends (rebuild + sync a S3)
cd frontend && npm run build -w @chessquery/ui-lib \
  && npm run build -w chess-portal && npm run build -w organizer-panel && cd ..
aws s3 sync frontend/apps/chess-portal/dist/    s3://chessquery-chess-portal    --delete
aws s3 sync frontend/apps/organizer-panel/dist/ s3://chessquery-organizer-panel --delete
```
> Las migraciones Flyway (p.ej. ms-game V5, ms-tournament V6) se aplican solas al
> arrancar los contenedores. Son aditivas.

---

## ⚙️ CI/CD (GitHub Actions + runner self-hosted)

```bash
# ¿El runner está online?
gh api repos/agusnoopy3000/ChessQuery_FS3/actions/runners \
  --jq '.runners[] | "\(.name) \(.status) busy=\(.busy)"'
# En la máquina del runner (servicio):
cd /opt/actions-runner && sudo ./svc.sh status      # active (running)

# Ver corridas / disparar workflows
gh run list --limit 5
gh run watch <run-id> --exit-status
gh workflow run deploy.yml                           # deploy es manual (workflow_dispatch)
```
- `ci.yml` y `build-and-push.yml` → se disparan en **push a `main`** (y CI también en PR a main).
- `deploy.yml` → **manual**. Refrescar Secrets AWS antes (Academy 4h).
- Un solo runner ejecuta jobs **en serie** (~15-20 min las 10 suites). Para paralelismo, sumar otro runner Linux x86_64.

---

## 🧪 Flujo de prueba sugerido (de cara al usuario)

1. **Registro**: entrar al front jugador → "Crear cuenta" (rol Jugador). Otra cuenta con rol Organizador.
2. **Recuperar contraseña**: Login → "¿Olvidaste?" → email → link `/reset-password` (depende del SMTP de Supabase; en demo puede tener rate limit).
3. **Partida casual + invitación**: crear partida en vivo → invitar por email (si el email es de un jugador registrado le llega notificación in-app; si no, compartir el link). *No crea cuentas en Supabase.*
4. **Torneo en vivo** (organizador): crear torneo → registrar 2+ jugadores → generar ronda → se crean partidas por mesa y los jugadores reciben notificación → el organizador ve **"👁 Ver en vivo"** (tablero espectador). Al terminar la partida, el **resultado vuelve solo** al pairing y a la clasificación (la grilla auto-refresca cada 8s).

---

## 📌 Pendientes conocidos
- **A4**: borrar usuarios de prueba en Supabase Auth (`martin@demo.cl`, `smoke+*`, `webhooktest+*`).
- **Producción** (documentado, no bloquea demo): HTTPS (ACM/CloudFront), SMTP propio + confirmación de email, secret del webhook a Vault, quitar regla SG `8080 0.0.0.0/0`, observabilidad.
