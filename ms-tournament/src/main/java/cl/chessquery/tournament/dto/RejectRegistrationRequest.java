package cl.chessquery.tournament.dto;

import jakarta.validation.constraints.Size;

/**
 * Body opcional al rechazar una inscripción. La razón se incluye en la
 * notificación al jugador y queda en logs.
 */
public record RejectRegistrationRequest(
        @Size(max = 500) String reason
) {}
