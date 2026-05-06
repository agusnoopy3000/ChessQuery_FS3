-- =============================================================================
-- Migración 00001: Tabla user_profiles y trigger de auto-creación
-- 
-- Propósito: Almacenar roles y metadata adicional de usuarios más allá de
-- auth.users. Se sincroniza automáticamente al registrar un usuario.
-- =============================================================================

-- Crear tabla user_profiles con FK a auth.users
CREATE TABLE public.user_profiles (
  id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  role TEXT NOT NULL CHECK (role IN ('PLAYER', 'ORGANIZER', 'ADMIN')),
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Comentarios de tabla
COMMENT ON TABLE public.user_profiles IS 'Perfiles de usuario con roles para ChessQuery';
COMMENT ON COLUMN public.user_profiles.id IS 'UUID del usuario, FK a auth.users(id)';
COMMENT ON COLUMN public.user_profiles.role IS 'Rol del usuario: PLAYER, ORGANIZER o ADMIN';
COMMENT ON COLUMN public.user_profiles.created_at IS 'Fecha de creación del perfil';

-- =============================================================================
-- Row Level Security (RLS)
-- =============================================================================

ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;

-- Policy: Usuarios autenticados pueden leer su propio perfil
CREATE POLICY "Users can read own profile"
  ON public.user_profiles
  FOR SELECT
  USING (auth.uid() = id);

-- Policy: Service role puede gestionar todos los perfiles (CRUD completo)
CREATE POLICY "Service role can manage all profiles"
  ON public.user_profiles
  FOR ALL
  USING (auth.role() = 'service_role');

-- =============================================================================
-- Trigger: Auto-crear perfil al registrar usuario en auth.users
-- =============================================================================

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO public.user_profiles (id, role)
  VALUES (
    NEW.id,
    COALESCE(NEW.raw_user_meta_data->>'role', 'PLAYER')
  );
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

COMMENT ON FUNCTION public.handle_new_user IS 'Auto-inserta perfil en user_profiles al crear usuario. Extrae role de raw_user_meta_data, default PLAYER.';

CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();
