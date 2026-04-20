-- =============================================================================
-- MS-Auth — V1: Tabla AUTH_USER
-- =============================================================================

CREATE TABLE auth_user (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('PLAYER', 'ORGANIZER', 'ADMIN')),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_auth_user_email ON auth_user (email);
