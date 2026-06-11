# ChessQuery — Setup de Supabase para una réplica independiente

> Complemento de `docs/SETUP_AWS_COMPLETO.md`. Para el escenario **réplica con redundancia**:
> tu compañero levanta su **propio proyecto Supabase** (hosted) apuntando a **su propio ALB**.
> Así su entorno queda 100% independiente del original.

---

## ⚠️ Consideraciones clave (leer antes)

1. **Proyecto Supabase propio.** En modo réplica, cada uno tiene su Supabase. NO compartir el
   proyecto del compañero original (si no, login compartido + datos partidos entre dos RDS).
2. **El webhook es lo que une Supabase ↔ AWS.** Cuando alguien se registra, Supabase llama por
   HTTP al `api-gateway` (vía ALB) para provisionar el jugador. Ese webhook debe apuntar al
   **ALB de TU cuenta** y firmar con el **mismo secret** que guarda TU AWS Secrets Manager.
3. **Extensión `pg_net`**: el webhook usa `net.http_post(...)`. Hay que **habilitar `pg_net`**
   en el proyecto, si no el trigger falla silenciosamente.
4. **El secret se inyecta como literal en el SQL** (en Supabase Cloud el SQL Editor no puede
   hacer `ALTER DATABASE ... SET app.settings.*`). Por eso se redefine la función con el secret
   escrito directo. **Nunca se commitea.**
5. **Orden importa:** primero corré `setup-aws.sh` en tu cuenta (genera el webhook secret en
   Secrets Manager), después lo copiás dentro de la función SQL de Supabase (§4).
6. **Header exacto:** `X-Supabase-Webhook-Secret` (NO `X-Webhook-Secret`). Es el que valida el
   `SupabaseWebhookController` del gateway.

---

## 1. Crear el proyecto Supabase
1. Entrar a <https://supabase.com> → **New project** (free tier alcanza).
2. Elegir región (idealmente cercana a `us-east-1`) y una **DB password**.
3. Esperar a que termine de aprovisionar (~2 min).

## 2. Obtener las claves (Settings → API)
Anotar:
- **Project URL** → `SUPABASE_URL` (ej. `https://abcd1234.supabase.co`)
- **anon public key** → `VITE_SUPABASE_ANON_KEY` (va al **frontend**)
- **service_role key** → `SUPABASE_SERVICE_KEY` (va al **backend**, ⚠️ es admin, nunca al front)
- **JWT Secret** (Settings → API → *JWT Settings* / *Legacy JWT secret*) → `SUPABASE_JWT_SECRET`

> Estos 3 (`SUPABASE_URL`, `SUPABASE_SERVICE_KEY`, `SUPABASE_JWT_SECRET`) son los que pide
> `setup-aws.sh` en el §3 del doc de AWS. `SUPABASE_URL` y `SUPABASE_JWT_SECRET` **deben ser
> del mismo proyecto** (el gateway valida el token contra ese proyecto).

## 3. Aplicar el esquema (SQL Editor del dashboard)
En **SQL Editor**, ejecutar en orden el contenido de `supabase/migrations/`:
1. `00001_create_user_profiles.sql` — tabla `public.user_profiles` + trigger `on_auth_user_created`.
2. `00002_create_storage_bucket.sql` — bucket de PGN.
3. `00004_admin_can_update_profiles.sql` — policies de ADMIN.

> Saltear `00003` (apunta al webhook local de Docker); en su lugar usar `00005` con TU ALB (§4).

Antes de seguir, habilitar la extensión del webhook:
```sql
create extension if not exists pg_net;
```

## 4. Configurar el webhook user.registered → TU ALB
1. Obtené el **secret** y el **DNS de tu ALB** (ya creados en tu cuenta AWS):
   ```bash
   # secret que generó setup-aws.sh en TU cuenta:
   aws secretsmanager get-secret-value --secret-id chessquery/supabase-webhook-secret \
     --query SecretString --output text
   # DNS de tu ALB:
   aws elbv2 describe-load-balancers --names chessquery-alb \
     --query 'LoadBalancers[0].DNSName' --output text
   ```
2. En el **SQL Editor**, pegá `00005_update_webhook_url_cloud.sql` pero **reemplazando**:
   - la `webhook_url` por **tu** ALB: `http://<TU-ALB-DNS>/webhooks/supabase/user-registered`
   - el `webhook_secret` por el **literal real** del paso anterior (en vez del `COALESCE`):
     ```sql
     webhook_secret TEXT := '<EL-SECRET-REAL-DE-TU-CUENTA>';
     ```
3. Ejecutar. El trigger `on_auth_user_registered_webhook` (de la 00003) ya queda apuntando a
   esta función redefinida.

> El secret de la función SQL **tiene que ser idéntico** al de `chessquery/supabase-webhook-secret`
> en tu AWS Secrets Manager (que es lo que el gateway usa para validar). Si no coinciden, el
> gateway rechaza el webhook y el jugador no se provisiona.

## 5. Metadata de registro (Lichess, club)
El frontend manda en el `signUp` el metadata del usuario. ChessQuery espera **camelCase**:
- `lichessUsername` (no `lichess_username`)
- `clubName`, `firstName`, `lastName`, `role`

No hace falta tocar nada en Supabase para esto (se guarda en `raw_user_meta_data` y viaja en el
webhook). El constraint `lichess_username UNIQUE` vive en la **RDS de ms-users**, no en Supabase.

## 6. Ajustes de Auth (recomendado para demo)
En **Authentication → Providers / Settings**:
- **Email confirmation**: para demo conviene **desactivarla** (autoconfirm) así el alta es directa.
  Para producción, activarla y configurar SMTP propio.
- **Minimum password length**: subir a **8** (corresponde al hallazgo OBS-02). Esto se hace en el
  dashboard o vía Management API con un PAT `sbp_` (el service_role NO sirve para esto).

## 7. Conectar el frontend a TU Supabase
Al buildear los frontends (§8 del doc de AWS), exportá las vars de TU proyecto:
```bash
export VITE_SUPABASE_URL=https://<tu-ref>.supabase.co
export VITE_SUPABASE_ANON_KEY=<anon key>
export VITE_API_BASE_URL=http://<TU-ALB-DNS>
cd frontend && npm install && npm run build && cd ..
# ...luego s3 sync a tus buckets
```

## 8. Verificar el flujo de punta a punta
1. Entrá a tu portal (S3) → **Registrate** con un email de prueba.
2. Eso dispara: Supabase crea el usuario → webhook → tu ALB → `api-gateway` →
   `ms-users` provisiona el jugador en tu RDS.
3. Verificá que el jugador exista (en tu cuenta):
   ```bash
   curl http://<TU-ALB-DNS>/actuator/health/readiness   # UP
   # y revisá los logs de ms-users en CloudWatch, o consultá la RDS:
   #   psql -h <TU-DB_HOST> -U chessquery -d users_db -c "select id,email,lichess_username from players order by id desc limit 5;"
   ```
4. Si el jugador NO aparece: 99% es el webhook → revisá que la URL del SQL apunte a TU ALB,
   que el secret coincida con Secrets Manager, y que `pg_net` esté habilitado.

---

## Checklist rápido (réplica Supabase)
- [ ] Proyecto Supabase creado, claves anotadas
- [ ] `SUPABASE_URL`/`SERVICE_KEY`/`JWT_SECRET` exportados antes de `setup-aws.sh`
- [ ] Migraciones 00001, 00002, 00004 aplicadas
- [ ] `pg_net` habilitada
- [ ] Función `notify_user_registered` redefinida con TU ALB + secret real (de TU cuenta)
- [ ] Email confirmation en autoconfirm (demo) + password min length = 8
- [ ] Frontend buildeado con TU `VITE_SUPABASE_URL`/`ANON_KEY`/`API_BASE_URL`
- [ ] Registro de prueba provisiona el jugador en TU RDS

## Referencias
- `supabase/migrations/` — SQL fuente (00001–00005).
- `docs/SUPABASE_SETUP.md` — setup de Supabase **local** (Docker), para desarrollo.
- `docs/SETUP_AWS_COMPLETO.md` — el resto de la infra.
