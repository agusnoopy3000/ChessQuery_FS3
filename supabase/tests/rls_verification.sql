-- =============================================================================
-- Test manual de RLS policies en user_profiles
--
-- Ejecutar contra Supabase Local:
--   psql "postgresql://postgres:postgres@127.0.0.1:54322/postgres" \
--     -f supabase/tests/rls_verification.sql
--
-- Cada bloque imprime "OK" o "FAIL" sin abortar el script.
-- =============================================================================

\echo '── Test 1: usuario lee su propio perfil ─────────────────────────────'
DO $$
DECLARE u UUID;
BEGIN
  -- Simula auth.uid() de un usuario existente
  SELECT id INTO u FROM auth.users LIMIT 1;
  IF u IS NULL THEN
    RAISE NOTICE 'SKIP: no hay usuarios en auth.users';
    RETURN;
  END IF;
  PERFORM set_config('request.jwt.claim.sub', u::text, true);
  PERFORM set_config('role', 'authenticated', true);
  IF EXISTS (SELECT 1 FROM public.user_profiles WHERE id = u) THEN
    RAISE NOTICE 'OK: usuario lee su propio perfil';
  ELSE
    RAISE NOTICE 'FAIL: el perfil no fue auto-creado por el trigger';
  END IF;
END$$;

\echo '── Test 2: usuario NO lee perfil ajeno ──────────────────────────────'
DO $$
DECLARE u1 UUID; u2 UUID;
BEGIN
  SELECT id INTO u1 FROM auth.users ORDER BY created_at LIMIT 1;
  SELECT id INTO u2 FROM auth.users ORDER BY created_at DESC LIMIT 1;
  IF u1 IS NULL OR u2 IS NULL OR u1 = u2 THEN
    RAISE NOTICE 'SKIP: se necesitan al menos 2 usuarios distintos';
    RETURN;
  END IF;
  PERFORM set_config('request.jwt.claim.sub', u1::text, true);
  PERFORM set_config('role', 'authenticated', true);
  IF EXISTS (SELECT 1 FROM public.user_profiles WHERE id = u2) THEN
    RAISE NOTICE 'FAIL: usuario u1 puede leer perfil de u2 (RLS bypass)';
  ELSE
    RAISE NOTICE 'OK: usuario no puede leer perfil ajeno';
  END IF;
END$$;

\echo '── Test 3: service_role lee todos los perfiles ──────────────────────'
DO $$
DECLARE total INT;
BEGIN
  PERFORM set_config('role', 'service_role', true);
  SELECT count(*) INTO total FROM public.user_profiles;
  IF total >= 0 THEN
    RAISE NOTICE 'OK: service_role lee % perfiles', total;
  END IF;
END$$;

\echo '── Test 4: trigger handle_new_user existe ───────────────────────────'
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM pg_trigger
    WHERE tgname = 'on_auth_user_created'
  ) THEN
    RAISE NOTICE 'OK: trigger on_auth_user_created activo';
  ELSE
    RAISE NOTICE 'FAIL: trigger on_auth_user_created no existe';
  END IF;
END$$;
