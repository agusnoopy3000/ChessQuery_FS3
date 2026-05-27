-- Bug #3: el flujo Supabase Auth dispara user.registered con userId=UUID
-- antes de que ms-users cree el Player numérico. Para notificaciones de
-- bienvenida (donde solo el email importa) toleramos recipient_id=NULL
-- y el caller decide si persistir la fila o saltarla.

ALTER TABLE notification_log
    ALTER COLUMN recipient_id DROP NOT NULL;
