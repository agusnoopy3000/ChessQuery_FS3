# Rollback de la migración a Supabase

Tiempo estimado total: **<1 hora**.

Restaura el stack pre-Supabase: MS-Auth (puerto 9090), `auth_db` (5432),
MinIO (9000/9001) y los flujos `/auth/login`, `/auth/register`, etc.

## 1. Detener el stack actual

```bash
cd infrastructure
docker compose down
```

## 2. Levantar servicios legacy

`docker-compose.backup.yml` contiene los servicios eliminados con tag
correspondiente al commit `0fb84d5` (rama `backup/ms-auth-pre-supabase`):

```bash
docker compose -f docker-compose.yml -f docker-compose.backup.yml up -d
```

Verificar que los nuevos contenedores responden:

```bash
curl http://localhost:9090/auth/validate -H "Authorization: Bearer x"  # 401 esperado
curl http://localhost:9000/minio/health/live                            # 200
```

## 3. Restaurar código backend a la rama backup

```bash
git checkout backup/ms-auth-pre-supabase   # base del estado pre-migración
# o cherry-pick selectivamente si querés conservar algunos cambios:
git revert --no-commit <commit-hash-supabase>...HEAD
git commit -m "revert: rollback Supabase migration"
```

Cambios que rollback debe deshacer:

- API Gateway: `SupabaseJwtAuthFilter`, webhook `user-registered`,
  config rabbitmq → restaurar `JwtAuthFilter` que llama a MS-Auth.
- MS-Game: `STORAGE_PROVIDER=supabase` → `minio`. `SupabaseStorageService`
  permanece en código pero no se inyecta.
- Frontends (chess-portal, organizer-panel): `createSupabaseApiClient` →
  `createApiClient` (storage local). `AuthProvider` con `client/storage`
  en lugar de `supabase`.
- MS-Users: opcionalmente revertir `V9__add_supabase_user_id.sql` con
  `ALTER TABLE player DROP COLUMN supabase_user_id` (no es necesario,
  la columna queda nullable y no estorba).

## 4. Migrar datos

### 4a. Usuarios (auth.users → auth_user)

```sql
-- En Supabase Postgres (54322)
COPY (
  SELECT id, email, encrypted_password, raw_user_meta_data->>'role' AS role,
         created_at, updated_at
  FROM auth.users
) TO '/tmp/users.csv' WITH CSV HEADER;
```

Cargar en auth_db (5432):

```sql
INSERT INTO auth_user (id, email, password_hash, role, created_at, updated_at)
SELECT
  -- generar BIGINT desde UUID estable
  ('x' || substr(md5(id::text), 1, 15))::bit(60)::bigint,
  email, encrypted_password, role, created_at, updated_at
FROM <import>;
```

### 4b. PGN files (Supabase Storage → MinIO)

```bash
# Descargar todo el bucket desde Supabase
supabase storage download chessquery-pgn ./pgn-export -r

# Subir a MinIO
mc alias set local http://localhost:9000 minioadmin minioadmin
mc cp -r ./pgn-export/ local/chessquery-pgn/
```

## 5. Validar el rollback

```bash
# Login con credenciales legacy
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"...","password":"..."}'

# Subir un PGN y verificar key MinIO
curl -X POST http://localhost:8080/api/player/games -d '...'
mc ls local/chessquery-pgn/games/
```

## 6. Limpiar Supabase local (opcional)

```bash
supabase stop --no-backup
docker volume prune -f
```

## Checklist final

- [ ] MS-Auth responde en `:9090`
- [ ] auth_db responde en `:5432`
- [ ] MinIO responde en `:9000` con bucket `chessquery-pgn`
- [ ] Frontends usan `localStorageTokenStorage` (no Supabase)
- [ ] API Gateway valida JWT vía MS-Auth (no JWT_SECRET local)
- [ ] Tests de smoke ok: register → login → upload PGN → download
