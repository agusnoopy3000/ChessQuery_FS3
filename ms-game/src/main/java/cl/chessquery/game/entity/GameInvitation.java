package cl.chessquery.game.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Invitación a una partida en vivo con ciclo de vida (EP-P1 / P1-03).
 *
 * PENDING es el estado inicial; una respuesta del invitado la lleva a
 * ACCEPTED/DECLINED, el vencimiento del TTL a EXPIRED, y una cancelación del
 * emisor a CANCELLED. Solo PENDING es transicionable.
 */
@Entity
@Table(name = "game_invitation")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class GameInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "from_player_id", nullable = false)
    private Long fromPlayerId;

    /** NULL cuando el invitado no tiene cuenta (solo email). */
    @Column(name = "to_player_id")
    private Long toPlayerId;

    @Column(name = "to_email", nullable = false)
    private String toEmail;

    /** Color que jugará el invitado ('w'/'b'). Por defecto negras (el creador es blancas). */
    @Column(name = "invitee_color", nullable = false)
    private String inviteeColor;

    @Column(name = "time_control_initial_ms")
    private Long timeControlInitialMs;

    @Column(name = "time_control_increment_ms")
    private Long timeControlIncrementMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvitationStatus status;

    @Column(name = "decline_reason")
    private String declineReason;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = InvitationStatus.PENDING;
        if (inviteeColor == null) inviteeColor = "b";
    }

    public enum InvitationStatus { PENDING, ACCEPTED, DECLINED, EXPIRED, CANCELLED }
}
