-- =============================================================================
-- Migración 00004: Permitir a ADMIN gestionar perfiles ajenos
--
-- Propósito: La policy "Service role can manage all profiles" sólo aplica
-- a llamadas con SERVICE_KEY (backend). Para que un usuario con role
-- ADMIN pueda actualizar el role de otros perfiles desde el panel de admin
-- (autenticado con su anon key + JWT), necesitamos una policy adicional.
--
-- Nota: el cambio del role se refleja en el próximo JWT cuando el usuario
-- afectado refresque la sesión (Supabase emite los claims desde
-- raw_user_meta_data, no desde public.user_profiles, por lo que también es
-- necesario actualizar auth.users.raw_user_meta_data — esto debe hacerlo
-- el backend con service key, no esta policy).
-- =============================================================================

CREATE POLICY "Admins can read all profiles"
  ON public.user_profiles
  FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM public.user_profiles p
      WHERE p.id = auth.uid() AND p.role = 'ADMIN'
    )
  );

CREATE POLICY "Admins can update all profiles"
  ON public.user_profiles
  FOR UPDATE
  USING (
    EXISTS (
      SELECT 1 FROM public.user_profiles p
      WHERE p.id = auth.uid() AND p.role = 'ADMIN'
    )
  );
