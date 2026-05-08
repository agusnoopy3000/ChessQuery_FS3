-- =============================================================================
-- MS-Tournament — V5: PENDING + REJECTED en RegistrationStatus + flag de aprobación
-- =============================================================================
--
-- Cambios:
--   1) tournament.requires_approval (boolean, default true): si es true, las
--      nuevas inscripciones quedan en PENDING y requieren aprobación del
--      organizador. Si es false, pasan directo a CONFIRMED.
--   2) tournament_registration.status: ampliamos el CHECK constraint para
--      aceptar PENDING y REJECTED. Los registros existentes quedan intactos.
-- =============================================================================

ALTER TABLE tournament
    ADD COLUMN IF NOT EXISTS requires_approval BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE tournament_registration
    DROP CONSTRAINT IF EXISTS tournament_registration_status_check;

ALTER TABLE tournament_registration
    ADD CONSTRAINT tournament_registration_status_check
    CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED'));
