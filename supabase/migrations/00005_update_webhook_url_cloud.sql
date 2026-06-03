-- =============================================================================
-- Migración 00005: Repuntar el webhook user.registered a AWS (ALB)
--
-- Contexto: la 00003 apunta a http://host.docker.internal:8080 (Docker local),
-- que NO resuelve desde Supabase Cloud. Aquí redefinimos la función para que
-- apunte al api-gateway publicado tras el ALB.
--
-- La URL y el secret se leen de settings de base de datos (app.settings.*) con
-- fallback al ALB. PERO ojo: en Supabase Cloud el rol del SQL Editor NO puede
-- ejecutar `ALTER DATABASE ... SET app.settings.*` (error 42501 permission
-- denied). Por eso, para inyectar el SECRET real en Cloud, redefiní la función
-- desde el SQL Editor con el secret como literal (ver snippet abajo). Esta
-- migración deja la función correcta en cuanto a URL/header; el secret real se
-- aplica aparte para no commitearlo al repo.
--
-- IMPORTANTE: el header DEBE ser 'X-Supabase-Webhook-Secret' — es el que valida
-- SupabaseWebhookController en el gateway. (No usar 'X-Webhook-Secret'.)
--
-- Inyección del secret real en Cloud (SQL Editor del dashboard) — el secret es
-- el mismo de chessquery/supabase-webhook-secret en AWS Secrets Manager:
--   CREATE OR REPLACE FUNCTION public.notify_user_registered() ... con
--   webhook_secret TEXT := '<secret>';  (literal, no current_setting)
-- =============================================================================

CREATE OR REPLACE FUNCTION public.notify_user_registered()
RETURNS TRIGGER AS $$
DECLARE
  webhook_url TEXT := COALESCE(
    current_setting('app.settings.webhook_url', true),
    'http://chessquery-alb-984810293.us-east-1.elb.amazonaws.com/webhooks/supabase/user-registered'
  );
  webhook_secret TEXT := COALESCE(
    current_setting('app.settings.webhook_secret', true),
    'dev-webhook-secret'
  );
  payload JSONB;
BEGIN
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

  PERFORM net.http_post(
    url := webhook_url,
    body := payload,
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'X-Supabase-Webhook-Secret', webhook_secret
    )
  );

  RETURN NEW;
EXCEPTION
  WHEN OTHERS THEN
    RAISE WARNING 'Webhook user.registered failed: %', SQLERRM;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

COMMENT ON FUNCTION public.notify_user_registered IS
  'Envía webhook HTTP al api-gateway (ALB AWS) cuando se registra un usuario; publica user.registered a RabbitMQ. Header X-Supabase-Webhook-Secret.';

-- El trigger on_auth_user_registered_webhook de la 00003 sigue válido
-- (apunta a esta misma función), no hace falta recrearlo.
