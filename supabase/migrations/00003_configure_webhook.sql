-- =============================================================================
-- Migración 00003: Database webhook para evento user.registered
-- 
-- Propósito: Configurar un trigger que notifica al API Gateway cuando un
-- nuevo usuario se registra, para que publique el evento user.registered
-- a RabbitMQ y mantenga compatibilidad con el flujo existente.
--
-- NOTA: Supabase Database Webhooks se implementan como triggers PostgreSQL
-- que llaman a pg_net (HTTP extension). El webhook se configura para
-- llamar al API Gateway en host.docker.internal:8080.
-- =============================================================================

-- Habilitar extensión pg_net para llamadas HTTP desde PostgreSQL
CREATE EXTENSION IF NOT EXISTS pg_net WITH SCHEMA extensions;

-- Función que envía webhook HTTP al API Gateway cuando se crea un usuario
CREATE OR REPLACE FUNCTION public.notify_user_registered()
RETURNS TRIGGER AS $$
DECLARE
  webhook_url TEXT := 'http://host.docker.internal:8080/webhooks/supabase/user-registered';
  webhook_secret TEXT;
  payload JSONB;
BEGIN
  -- Obtener el webhook secret de la configuración
  -- En producción, usar vault o variable de entorno
  webhook_secret := current_setting('app.settings.webhook_secret', true);
  
  -- Construir payload con datos del nuevo usuario
  payload := jsonb_build_object(
    'type', 'INSERT',
    'table', 'users',
    'schema', 'auth',
    'record', jsonb_build_object(
      'id', NEW.id,
      'email', NEW.email,
      'raw_user_meta_data', NEW.raw_user_meta_data,
      'created_at', NEW.created_at
    ),
    'old_record', NULL
  );

  -- Enviar webhook HTTP via pg_net
  PERFORM extensions.http_post(
    url := webhook_url,
    body := payload::TEXT,
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'X-Supabase-Webhook-Secret', COALESCE(webhook_secret, 'dev-webhook-secret')
    )
  );

  RETURN NEW;
EXCEPTION
  WHEN OTHERS THEN
    -- No bloquear el registro si el webhook falla
    RAISE WARNING 'Webhook user.registered failed: %', SQLERRM;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

COMMENT ON FUNCTION public.notify_user_registered IS 'Envía webhook HTTP al API Gateway cuando se registra un usuario para publicar evento user.registered a RabbitMQ';

-- Trigger que se ejecuta DESPUÉS de insertar en auth.users
-- (después del trigger on_auth_user_created que crea el perfil)
CREATE TRIGGER on_auth_user_registered_webhook
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.notify_user_registered();
