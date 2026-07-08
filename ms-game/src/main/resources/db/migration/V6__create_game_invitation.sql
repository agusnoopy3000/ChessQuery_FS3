-- =============================================================================
-- MS-Game — V6: Invitaciones a partida en vivo (game_invitation)
--
-- Formaliza el flujo de invitación (EP-P1 / P1-03). Antes la invitación era
-- fire-and-forget (solo push game.invitation). Ahora cada invitación tiene ciclo
-- de vida con expiración (TTL): PENDING → ACCEPTED / DECLINED / EXPIRED / CANCELLED.
-- Un job programado marca EXPIRED las PENDING vencidas y publica invite.expired.
-- =============================================================================

CREATE TABLE game_invitation (
    id                        BIGSERIAL    PRIMARY KEY,
    session_id                BIGINT       NOT NULL
                              REFERENCES live_game_session (id) ON DELETE CASCADE,
    from_player_id            BIGINT       NOT NULL,
    to_player_id              BIGINT,                 -- NULL: invitado sin cuenta (solo email)
    to_email                  VARCHAR(255) NOT NULL,
    invitee_color             CHAR(1)      NOT NULL DEFAULT 'b' CHECK (invitee_color IN ('w', 'b')),
    time_control_initial_ms   BIGINT,
    time_control_increment_ms BIGINT,
    status                    VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                              CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'CANCELLED')),
    decline_reason            VARCHAR(255),
    expires_at                TIMESTAMPTZ  NOT NULL,
    responded_at              TIMESTAMPTZ,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- El scheduler de expiración filtra por (status, expires_at): índice compuesto.
CREATE INDEX idx_gi_status_expires ON game_invitation (status, expires_at);
CREATE INDEX idx_gi_session        ON game_invitation (session_id);
CREATE INDEX idx_gi_to_player      ON game_invitation (to_player_id);

COMMENT ON TABLE game_invitation IS 'Invitaciones a partida en vivo con TTL y máquina de estados (P1-03).';
