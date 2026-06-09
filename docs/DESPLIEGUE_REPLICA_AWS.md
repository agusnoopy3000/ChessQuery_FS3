# Despliegue de la réplica en AWS (cuenta propia)

> ℹ️ **Este es el despliegue de _Martin_** (cuenta propia `876204681432`). El despliegue
> **original es de _Agustín_** (cuenta `672782205900`) y está en
> [`DESPLIEGUE_AWS_REALIZADO.md`](DESPLIEGUE_AWS_REALIZADO.md). Misma arquitectura, endpoints
> distintos: **operá siempre con este doc**, no con el de Agustín.

> Registro de la **réplica independiente** de ChessQuery levantada en una cuenta
> AWS + Supabase **propia**, separada de la cuenta del despliegue original. Camino
> usado: los scripts bash del repo (`setup-aws.sh` → `build-push-ecr.sh` →
> `create-ecs-service.sh`) + ALB + S3, con un proyecto Supabase Cloud propio.
> Fecha del despliegue: 2026-06-09.
>
> Este documento es **auto-contenido** para la operación día a día (no depende de
> guías locales). El procedimiento de instalación desde cero está, además, en las
> guías de setup del equipo.

---

## 1. Configuración actual (lo que quedó desplegado)

> Solo datos públicos (endpoints, nombres de recursos). **Ningún secreto** vive en
> este archivo: los secretos están en AWS Secrets Manager y los ARNs/endpoints
> locales en `infrastructure/aws/.deploy-outputs.env` (gitignored).

| Capa | Recurso |
|---|---|
| **Región / cuenta** | `us-east-1` |
| **Frontend (S3 website)** | `chessquery-chess-portal-mmora`, `chessquery-organizer-panel-mmora` |
| **Backend (ALB)** | `chessquery-alb` → ECS Fargate |
| **Cómputo** | ECS cluster `chessquery-cluster`, service `chessquery-stack` (1 task, 10 contenedores) |
| **Imágenes** | ECR `chessquery/*`, tag `v0.3.0` |
| **Base de datos** | RDS PostgreSQL `chessquery-pg` (6 bases: users, tournament, game, notifications, analytics, etl) |
| **Auth** | Proyecto Supabase Cloud propio (webhook `user.registered` → ALB) |
| **Secretos** | `chessquery/{db-password, rabbitmq-password, supabase-service-key, jwt-secret, supabase-webhook-secret, smtp-password}` |

**URLs:**

| Qué | URL |
|---|---|
| Portal de jugador | http://chessquery-chess-portal-mmora.s3-website-us-east-1.amazonaws.com |
| Panel de organizador | http://chessquery-organizer-panel-mmora.s3-website-us-east-1.amazonaws.com |
| Backend (API, vía ALB) | http://chessquery-alb-453727502.us-east-1.elb.amazonaws.com |

> Es **HTTP** (sin candado). El navegador puede avisar "no seguro": esperado en esta
> etapa. El backend en `/` devuelve 404 (es una API, no un sitio); responde en rutas
> como `/actuator/health/readiness`, `/auth/*`, `/api/*`, `/webhooks/*`.

### 1.1 Red y Security Groups (cómo está protegido)

Toda la infra vive en la **VPC default** `vpc-09d87e33242cc5a64` (`us-east-1`). El tráfico
se controla con **3 Security Groups** (los "firewalls" por recurso). La idea de diseño es
una **cadena**: internet solo entra por el ALB → el ALB es el único que habla con la task ECS
→ la task es la única que habla con la RDS.

| Security Group | ID | Protege | Regla de entrada (ingress) |
|---|---|---|---|
| `chessquery-alb-sg` | `sg-03a7938a6d1e3671f` | el **ALB** | TCP **80** desde `0.0.0.0/0` (internet) |
| `chessquery-ecs-sg` | `sg-0b894230fdd04ecf4` | la **task ECS** (gateway :8080) | TCP **8080** desde el SG del ALB (`sg-03a7938a6d1e3671f`) |
| `chessquery-rds-sg` | `sg-0fab20772139a82ac` | la **RDS** (Postgres :5432) | TCP **5432** desde la VPC (`172.31.0.0/16`) + la IP del dev (`179.60.74.4/32`) |

```
                80/tcp                 8080/tcp                 5432/tcp
   🌐 internet ───────► [ALB SG] ───────────► [ECS SG] ───────────► [RDS SG]
                       sg-03a793…           sg-0b8942…            sg-0fab20…
                    (abierto al mundo)   (solo desde ALB SG)   (solo VPC + IP dev)
```

Detalles importantes:
- **Por qué `8080 desde el SG del ALB` y no desde una IP:** referenciar el SG del ALB hace que
  la regla siga funcionando aunque cambie la IP de la task o del ALB. Es la práctica correcta.
- **La RDS es `PubliclyAccessible: true`** (para poder conectarse con `psql` desde la máquina del
  dev en Academy), pero el SG solo deja entrar desde **la VPC** y **una IP concreta** — no está
  abierta al mundo. Si cambia tu IP de casa, hay que actualizar el `/32` (ver abajo).
- ✅ **Hardening aplicado (2026-06-09):** se **quitó** la regla vieja `8080 desde 0.0.0.0/0` del
  `chessquery-ecs-sg`. Ahora el gateway **solo es accesible vía ALB** (no se puede saltar el ALB
  pegándole directo por IP). Verificado: el ALB sigue respondiendo `UP`. Comando usado:
  ```bash
  aws ec2 revoke-security-group-ingress --group-id sg-0b894230fdd04ecf4 \
    --protocol tcp --port 8080 --cidr 0.0.0.0/0
  ```

Actualizar tu IP en el SG de la RDS (si dejaste de conectar con `psql`):
```bash
MIIP=$(curl -s https://checkip.amazonaws.com)
aws ec2 authorize-security-group-ingress --group-id sg-0fab20772139a82ac \
  --protocol tcp --port 5432 --cidr "${MIIP}/32"
```

---

## 2. Operación día a día (prender / apagar para no gastar)

Con créditos de **AWS Academy**, la app **no queda prendida 24/7**. Lo que cuesta es
**ECS (Fargate)** y **RDS**: esos se apagan; ALB y S3 cuestan centavos y quedan activos
(así las URLs no cambian).

> ⚠️ Las credenciales de Academy **vencen cada ~4 h**. Si un comando falla con
> `voc-cancel-cred` / `ExpiredToken`, refrescá las credenciales (ver §3) y reintentá.

### Prender
```bash
# 1) Base de datos (tarda ~unos minutos en quedar 'available')
aws rds start-db-instance --db-instance-identifier chessquery-pg
aws rds wait db-instance-available --db-instance-identifier chessquery-pg

# 2) Backend (levanta la task con sus 10 contenedores)
aws ecs update-service --cluster chessquery-cluster --service chessquery-stack \
  --desired-count 1 --force-new-deployment

# 3) Verificar (esperar ~3-5 min a que el gateway quede UP)
curl http://chessquery-alb-453727502.us-east-1.elb.amazonaws.com/actuator/health/readiness
# → {"status":"UP"}
```

### Apagar
```bash
aws ecs update-service --cluster chessquery-cluster --service chessquery-stack --desired-count 0
aws rds stop-db-instance --db-instance-identifier chessquery-pg
```

---

## 3. Tareas frecuentes

### Refrescar credenciales de AWS Academy (cada ~4 h)
Copiar las 3 líneas de "AWS Details → AWS CLI" del lab y:
```bash
aws configure set aws_access_key_id     <ID>
aws configure set aws_secret_access_key <SECRET>
aws configure set aws_session_token     <TOKEN>
aws configure set region us-east-1
aws sts get-caller-identity   # confirma la cuenta
```

### Desplegar una versión nueva del backend
```bash
export IMAGE_TAG=v0.3.1                 # un tag nuevo
bash scripts/build-push-ecr.sh          # build + push de las 8 imágenes a ECR

export SUPABASE_URL=https://<tu-ref>.supabase.co
bash scripts/create-ecs-service.sh      # re-registra task-def + force-new-deployment
```

### Republicar los frontends
```bash
cd frontend
npm run build -w @chessquery/ui-lib
npm run build -w chess-portal
npm run build -w organizer-panel
cd ..
aws s3 sync frontend/apps/chess-portal/dist/    s3://chessquery-chess-portal-mmora    --delete
aws s3 sync frontend/apps/organizer-panel/dist/ s3://chessquery-organizer-panel-mmora --delete
```

### Ver un usuario / verificar provisión
- **Login (Supabase):** Dashboard → Authentication → Users.
- **Perfil de jugador (RDS):** se crea por el webhook al registrarse. Para consultarlo:
```bash
source infrastructure/aws/.deploy-outputs.env
DB_PW=$(aws secretsmanager get-secret-value --secret-id chessquery/db-password --query SecretString --output text)
PGPASSWORD="$DB_PW" psql -h "$DB_HOST" -U chessquery -d users_db \
  -c "SELECT id, email, first_name, lichess_username, created_at FROM player ORDER BY id DESC LIMIT 5;"
```

---

## 4. Pendientes

- **HTTPS** — todo corre en HTTP. Plan y opciones en `docs/PENDIENTE_HTTPS.md`.
- **SMTP real** — si `setup-aws.sh` se corrió sin `SMTP_USERNAME`/`SMTP_PASSWORD`, el
  secreto `chessquery/smtp-password` queda con un placeholder y los correos de
  bienvenida/invitación fallan. Setear la App Password real de Gmail y redesplegar.
- **Confirmar** `Minimum password length = 8` en Supabase (Auth → Sign In / Providers → Email).

---

## 5. Correcciones encontradas (gotchas que las guías base no cubrían)

Aprendizajes de este despliegue, útiles para la próxima réplica:

1. **Service-linked role de ECS.** El primer `create-cluster` con `FARGATE_SPOT` puede
   fallar con *"Unable to assume the service linked role"* en cuentas nuevas. El rol
   `AWSServiceRoleForECS` se crea/propaga solo: **reintentar** el script (es idempotente),
   o forzarlo con `aws iam create-service-linked-role --aws-service-name ecs.amazonaws.com`.

2. **Nombres de bucket S3 son globales.** `chessquery-chess-portal` y
   `chessquery-organizer-panel` ya están tomados por el despliegue original → usar un
   **sufijo propio** (acá `-mmora`). Como las URLs cross-app se hornean en el build,
   hay que poner las URLs propias en `VITE_ORGANIZER_URL` / `VITE_PORTAL_URL` y **rebuildear**.

3. **Variables que faltaban tras el merge de Lichess.** El task-def pasó a pedir
   `SMTP_USERNAME` / `SMTP_PASSWORD` y `GATEWAY_CORS_ALLOWED_ORIGINS`, pero los scripts
   no los creaban/exportaban (el secret SMTP vacío hace fallar el `register-task-def`).
   **Corregido** en `setup-aws.sh` (crea `chessquery/smtp-password`) y en
   `create-ecs-service.sh` (exporta esas 3 vars para `envsubst`).

4. **La tabla de jugadores es `player`** (singular), no `players`.

5. **El webhook necesita el trigger de la migración 00003.** La 00005 solo redefine la
   función (apuntándola al ALB); el **trigger** `on_auth_user_registered_webhook` se crea
   en la 00003. No saltear la 00003 del todo: aplicar 00001 + 00002 + 00004 + `pg_net` +
   la función con ALB/secret reales + el trigger.

6. **Supabase con JWT Signing Keys (ES256/JWKS).** Si el proyecto ya migró a las nuevas
   claves asimétricas (ECC P-256), el gateway igual valida los logins vía JWKS
   (`SupabaseJwtAuthFilter` lo soporta, con fallback HMAC). Como `SUPABASE_JWT_SECRET` se
   pasa el **Legacy JWT Secret** (sirve de fallback).

---

## 6. Referencias
- `scripts/setup-aws.sh`, `scripts/build-push-ecr.sh`, `scripts/create-ecs-service.sh`
- `docs/FRONTEND_S3_Y_ALB.md` — ALB + S3 paso a paso
- `docs/INFRA_PARA_EL_EQUIPO.md` — visión general no técnica
- `docs/PENDIENTE_HTTPS.md` — plan de HTTPS
- `docs/SECURITY_AUDIT_REPORT.md` — H-03 (secretos reales en el deploy)
