-- =============================================================================
-- MS-Auth — V2: Tabla REFRESH_TOKEN
-- Almacena el hash SHA-256 del refresh token (nunca el valor en claro).
-- Un usuario puede tener múltiples tokens activos (uno por dispositivo).
-- =============================================================================

CREATE TABLE refresh_token (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES auth_user (id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    is_revoked  BOOLEAN      NOT NULL DEFAULT FALSE,
    device_info VARCHAR(200),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_token_user_id ON refresh_token (user_id);
CREATE INDEX idx_refresh_token_hash    ON refresh_token (token_hash);
-- Índice para limpieza periódica de tokens expirados/revocados
CREATE INDEX idx_refresh_token_cleanup ON refresh_token (expires_at, is_revoked);
