# Plan — Migración a Supabase Cloud

Guía paso a paso para mover ChessQuery de **Supabase local** (containers en tu máquina vía `supabase start`) a **Supabase Cloud** (servicio managed en `supabase.com`), manteniendo compatibilidad con el desarrollo local.

> **Estado actual:** Supabase corre como containers locales en puerto 54321. Los MS lo consumen vía `http://host.docker.internal:54321`. Migraciones SQL están en `supabase/migrations/`.
> **Estado objetivo:** Supabase corre en `https://<proyecto>.supabase.co`. Los MS desplegados en ECS lo consumen vía HTTPS. El dev local sigue funcionando con Supabase local.

---

## 0. Pre-requisitos

```bash
# CLI de Supabase
npm install -g supabase                 # o brew install supabase/tap/supabase
supabase --version                      # >= 1.150

# Credenciales (las pedirá el primer login)
supabase login                          # abre browser, link con tu cuenta
```

---

## 1. Crear el proyecto en Supabase Cloud (5 min)

1. Andar a https://supabase.com/dashboard
2. **New Project**:
   - Name: `chessquery-prod`
   - DB Password: generar uno fuerte (anotalo, lo usás en RDS también si querés unificar)
   - Region: `South America (São Paulo)` (la más cercana a Chile)
   - Plan: **Free** (500 MAU + 1 GB DB + 1 GB Storage — alcanza para la demo)
3. Esperar ~2 min a que provisione.
4. **Settings → API**, anotar:
   - `Project URL` → será tu `SUPABASE_URL`
   - `anon public` key → `SUPABASE_ANON_KEY`
   - `service_role secret` key → `SUPABASE_SERVICE_KEY` ⚠️ NUNCA al frontend
5. **Settings → API → JWT Settings**, anotar:
   - `JWT Secret` → `SUPABASE_JWT_SECRET`

> Guardar los 4 valores en un gestor seguro (1Password, Bitwarden) — los vas a usar varias veces.

---

## 2. Replicar el schema y storage (10 min)

### 2.1 Linkear el proyecto local con el cloud

```bash
cd /home/agustingcastro/ChessQuery_FS3
supabase link --project-ref <tu-project-ref>
# project-ref es la parte antes de .supabase.co (ej: abcdefghijklmnop)
```

### 2.2 Subir las 4 migraciones existentes

```bash
# Verificar diff antes de aplicar
supabase db diff --linked

# Aplicar las migraciones supabase/migrations/*.sql al cloud
supabase db push
# Esto sube:
#   00001_create_user_profiles.sql
#   00002_create_storage_bucket.sql
#   00003_configure_webhook.sql        ⚠️ ver §3 — URL del webhook cambia
#   00004_admin_can_update_profiles.sql
```

### 2.3 Crear el bucket de storage

El bucket `chessquery-pgn` se crea en `00002_create_storage_bucket.sql`. Si Supabase Cloud no lo creó automáticamente:

1. Dashboard → **Storage** → **New bucket**
2. Name: `chessquery-pgn`
3. Public: **OFF** (acceso vía signed URLs desde ms-game)
4. File size limit: 5 MB
5. Allowed MIME types: `application/x-chess-pgn, text/plain`

### 2.4 Verificar

```bash
# Confirmar tablas
supabase db remote commit              # debe mostrar 0 cambios pendientes
# Dashboard → Table Editor → debe mostrar: user_profiles
# Dashboard → Storage → debe listar: chessquery-pgn
```

---

## 3. Ajustar el webhook `user.registered` (CRÍTICO)

El webhook actual llama a `http://host.docker.internal:8080/webhooks/supabase/user-registered`. Desde Supabase Cloud eso no resuelve. Hay que apuntarlo a la URL pública del api-gateway en AWS (o ngrok si todavía no hay ALB).

### 3.1 Crear nueva migración

```bash
cat > supabase/migrations/00005_update_webhook_url_cloud.sql <<'EOF'
-- Actualiza la URL del webhook user.registered para apuntar al api-gateway en AWS
-- (o a un túnel ngrok mientras el ALB no está activo).

CREATE OR REPLACE FUNCTION public.notify_user_registered()
RETURNS TRIGGER AS $$
DECLARE
  webhook_url TEXT := COALESCE(
    current_setting('app.settings.webhook_url', true),
    'https://api.chessquery.tu-dominio.com/webhooks/supabase/user-registered'
  );
  webhook_secret TEXT := COALESCE(
    current_setting('app.settings.webhook_secret', true),
    'CHANGE_ME'
  );
  payload JSONB;
BEGIN
  payload := jsonb_build_object(
    'type', 'INSERT',
    'table', 'auth.users',
    'record', row_to_json(NEW),
    'schema', 'auth'
  );

  PERFORM net.http_post(
    url := webhook_url,
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'X-Webhook-Secret', webhook_secret
    ),
    body := payload
  );
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
EOF

supabase db push
```

### 3.2 Setear los settings en el cloud DB

```bash
# Desde el SQL Editor del dashboard:
ALTER DATABASE postgres SET app.settings.webhook_url     = 'https://<URL-publica-api-gateway>/webhooks/supabase/user-registered';
ALTER DATABASE postgres SET app.settings.webhook_secret  = '<el-mismo-secret-que-en-AWS-Secrets-Manager>';
SELECT pg_reload_conf();
```

> Si todavía no hay ALB público (estás trabajando local), podés usar **ngrok** temporalmente:
> ```bash
> ngrok http 8080
> # Copiar la URL https://xxxx.ngrok.io y usarla como webhook_url
> ```

---

## 4. Actualizar variables de entorno

### 4.1 `.env` local del equipo (DEV — sigue usando Supabase local)

**No tocar** `infrastructure/.env.example`. Cada dev sigue con `supabase start` localmente.

### 4.2 AWS Secrets Manager (PROD)

```bash
export SUPABASE_URL=https://<tu-ref>.supabase.co
export SUPABASE_SERVICE_KEY=<service-role-key>
export SUPABASE_JWT_SECRET=<jwt-secret>
export SUPABASE_ANON_KEY=<anon-key>
export SUPABASE_WEBHOOK_SECRET=$(openssl rand -base64 32)

# Crear/actualizar secrets (los crea §1.6 del RUNBOOK_ECS, esto los actualiza con cloud)
aws secretsmanager update-secret --secret-id chessquery/supabase-service-key  --secret-string "$SUPABASE_SERVICE_KEY"
aws secretsmanager update-secret --secret-id chessquery/supabase-jwt-secret    --secret-string "$SUPABASE_JWT_SECRET"
aws secretsmanager update-secret --secret-id chessquery/supabase-webhook-secret --secret-string "$SUPABASE_WEBHOOK_SECRET"
# El SUPABASE_URL no es secret, va como variable de entorno plana en la task definition
```

### 4.3 Task definitions ECS

Editar `infrastructure/aws/task-definitions/*.template.json`:

| Archivo | Cambio |
|---|---|
| `api-gateway.template.json` | `SUPABASE_URL` env: agregar `https://${SUPABASE_PROJECT_REF}.supabase.co`. Agregar secret `SUPABASE_JWT_SECRET` |
| `ms-game.template.json` | `SUPABASE_URL` ya existe — solo cambiar el valor renderizado en CI |
| `ms-users.template.json` | Agregar env `SUPABASE_WEBHOOK_SECRET` desde secret |
| `ms-notifications.template.json` | Igual que ms-users si valida webhook |

Exportar la variable al renderizar templates:

```bash
cd infrastructure/aws/task-definitions
export SUPABASE_URL=https://<tu-ref>.supabase.co
for f in *.template.json; do envsubst < $f > rendered-${f%.template.json}.json; done
```

### 4.4 Frontend (build-time vars)

`frontend/apps/chess-portal/.env.production`:
```
VITE_SUPABASE_URL=https://<tu-ref>.supabase.co
VITE_SUPABASE_ANON_KEY=<anon-key>
```

Mismo archivo para `organizer-panel`. Estas se inyectan en `npm run build` y quedan compiladas en el bundle.

---

## 5. Validación local antes de subir a AWS

```bash
# 1. Bajar Supabase local
supabase stop

# 2. Editar infrastructure/.env temporalmente apuntando a cloud:
SUPABASE_URL=https://<tu-ref>.supabase.co
SUPABASE_ANON_KEY=<anon-key>
SUPABASE_SERVICE_KEY=<service-key>
SUPABASE_JWT_SECRET=<jwt-secret>

# 3. Levantar solo la stack de MS (sin supabase local)
cd infrastructure
docker compose up -d --build

# 4. Smoke E2E
curl -X POST https://<tu-ref>.supabase.co/auth/v1/signup \
  -H "apikey: <anon-key>" \
  -H "Content-Type: application/json" \
  -d '{"email":"test@chessquery.cl","password":"Test12345!"}'

# 5. Verificar que ms-users recibió el webhook y creó player_profile
docker compose logs ms-users | grep "user.registered"

# 6. Reset cuando termines testing
supabase start
# Revertir infrastructure/.env a valores locales
```

---

## 6. Despliegue a AWS ECS

```bash
# 1. Re-render task definitions con valores cloud (§4.3)
# 2. Registrar nuevas versiones
for f in rendered-*.json; do
  aws ecs register-task-definition --cli-input-json file://$f
done

# 3. Force redeploy de cada service
for s in api-gateway ms-users ms-game ms-tournament ms-notifications ms-analytics; do
  aws ecs update-service --cluster chessquery-cluster --service $s --force-new-deployment
done

# 4. Esperar y verificar
aws ecs wait services-stable --cluster chessquery-cluster --services api-gateway ms-users ms-game
curl https://<ALB-DNS>/actuator/health
```

---

## 7. Rollback a Supabase local (si algo falla)

```bash
# Local (dev): basta con
supabase start

# AWS: actualizar secrets de vuelta a valores dummy y bajar services
aws ecs update-service --cluster chessquery-cluster --service api-gateway --desired-count 0
```

> Mientras NO bajes los containers de Supabase local en tu equipo de desarrollo, podés seguir trabajando local sin interferir con cloud.

---

## 8. Checklist final

- [ ] Proyecto creado en Supabase Cloud (region São Paulo, plan Free)
- [ ] 4 keys anotadas: URL, anon, service, jwt-secret
- [ ] `supabase link` ejecutado
- [ ] `supabase db push` sin errores → 5 migraciones aplicadas
- [ ] Bucket `chessquery-pgn` creado y privado
- [ ] Webhook URL apuntando al api-gateway público (ALB o ngrok)
- [ ] Secrets en AWS Secrets Manager actualizados
- [ ] Task definitions re-renderizadas y registradas
- [ ] Frontend `.env.production` con URL cloud
- [ ] Smoke E2E desde compose local apuntando a cloud → OK
- [ ] Deploy ECS exitoso, `/actuator/health` UP
- [ ] Signup → user_profile creado → notificación welcome enviada

---

## 9. Costo y límites del plan Free

| Recurso | Free | Suficiente para demo? |
|---|---|---|
| MAU (Monthly Active Users) | 50,000 | ✅ sobra |
| Database size | 500 MB | ✅ suficiente |
| Storage | 1 GB | ✅ ~5,000 PGN files |
| Egress | 5 GB/mes | ✅ |
| Edge function invocations | 500k/mes | ✅ no usamos edge functions |
| Realtime concurrent | 200 connections | ✅ |
| Backups | 7 días automáticos | ✅ |
| Pausa por inactividad | después de 7 días sin actividad | ⚠️ → un cron simple que haga GET cada 6 días previene la pausa |

Cuando crezca el proyecto: Pro plan = USD 25/mes, sin pausa, 8 GB DB, 100 GB storage.

---

## 10. Riesgos y mitigaciones

| Riesgo | Probabilidad | Mitigación |
|---|---|---|
| Webhook no llega (firewall/ALB) | Alta inicial | Probar con ngrok antes de promover a ALB |
| RLS bloquea queries antes verificadas en local | Media | Replicar políticas RLS exactamente (`supabase db push` lo hace) |
| JWKS endpoint cambia (`/auth/v1/.well-known/jwks.json`) | Baja | Validar en `SupabaseJwtAuthFilter` antes del deploy |
| Cuota free agotada por bots | Baja | Habilitar CAPTCHA en signup (dashboard → Auth → Settings) |
| Storage bucket público por error | Crítica si pasa | Verificar `Public: OFF` en bucket settings |
| Service key filtrada al frontend | Crítica si pasa | Tests CI: `grep -r "service_role" frontend/` debe ser 0 |
