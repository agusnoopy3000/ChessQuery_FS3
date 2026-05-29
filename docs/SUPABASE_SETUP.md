# Setup de Supabase para ChessQuery

Esta guía explica cómo levantar el entorno local de Supabase que reemplaza
a MS-Auth y MinIO desde la migración (commits `feat: migrar validación JWT…`
y siguientes).

## 1. Instalar Supabase CLI

```bash
# macOS (Homebrew)
brew install supabase/tap/supabase

# Linux / Windows: ver https://supabase.com/docs/guides/cli
supabase --version   # >= 1.150
```

## 2. Inicializar y arrancar

Desde la raíz del repo:

```bash
supabase start
```

Levanta Postgres (`54322`), Auth (`54321`), Storage, Studio (`54323`) y la API
REST. La primera vez descarga las imágenes (~2 GB).

Aplica las migraciones del directorio `supabase/migrations/`:

- `00001_create_user_profiles.sql` — tabla `public.user_profiles` con RLS
  y trigger `on_auth_user_created`
- `00002_create_storage_bucket.sql` — bucket `chessquery-pgn`
- `00003_configure_webhook.sql` — webhook de `auth.users` → API Gateway
- `00004_admin_can_update_profiles.sql` — policies adicionales para ADMIN

## 3. Obtener API keys y JWT secret

```bash
supabase status
```

Imprime:

- `API URL`             → `SUPABASE_URL`            (ej. `http://127.0.0.1:54321`)
- `anon key`            → `VITE_SUPABASE_ANON_KEY`  (frontend)
- `service_role key`    → `SUPABASE_SERVICE_KEY`    (backend, NO al frontend)
- `JWT secret`          → `SUPABASE_JWT_SECRET`     (API Gateway)

Volcar al `.env` de cada componente:

```bash
# infrastructure/.env
SUPABASE_URL=http://host.docker.internal:54321
SUPABASE_JWT_SECRET=<jwt secret>
SUPABASE_SERVICE_KEY=<service_role key>
SUPABASE_WEBHOOK_SECRET=dev-webhook-secret
STORAGE_PROVIDER=supabase

# frontend/apps/chess-portal/.env y organizer-panel/.env
VITE_SUPABASE_URL=http://127.0.0.1:54321
VITE_SUPABASE_ANON_KEY=<anon key>
```

## 4. Verificar bucket y RLS

```bash
# Verificación rápida de RLS contra Supabase Local
psql "postgresql://postgres:postgres@127.0.0.1:54322/postgres" \
  -f supabase/tests/rls_verification.sql
```

Para inspeccionar en Studio: <http://127.0.0.1:54323>.

## 5. Rotar el webhook secret (producción)

El webhook `auth.users` → `POST {API_GATEWAY}/webhook/user-registered`
firma cada request con `X-Webhook-Secret`. En producción:

1. Editar el webhook en Studio (Database → Webhooks).
2. Actualizar `SUPABASE_WEBHOOK_SECRET` en el `.env` del gateway.
3. Reiniciar `api-gateway`.

## 6. Rollback

Para volver al stack pre-Supabase, recuperá el estado del commit `0fb84d5` (incluía MS-Auth, `auth_db` y MinIO).
