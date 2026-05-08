package cl.chessquery.tournament.entity;

/**
 * Estados de inscripción a un torneo.
 *
 * <p>Flujo cuando {@code Tournament.requiresApproval = true}:
 * <ol>
 *   <li>Jugador se inscribe → {@code PENDING}.</li>
 *   <li>Organizador aprueba → {@code CONFIRMED} (cuenta para cupos y standings).</li>
 *   <li>Organizador rechaza → {@code REJECTED} (terminal).</li>
 *   <li>Jugador cancela su inscripción → {@code CANCELLED}.</li>
 * </ol>
 *
 * <p>Si {@code requiresApproval = false}, el flujo se simplifica: la inscripción
 * pasa directo a {@code CONFIRMED} y nunca pasa por {@code PENDING}.
 */
public enum RegistrationStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    CANCELLED
}
