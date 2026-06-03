package cl.chessquery.game.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "live_game_session")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class LiveGameSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "white_player_id", nullable = false)
    private Long whitePlayerId;

    @Column(name = "black_player_id")
    private Long blackPlayerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "initial_fen", nullable = false, length = 120)
    private String initialFen;

    @Column(name = "current_fen", nullable = false, length = 120)
    private String currentFen;

    @Column(nullable = false, length = 1)
    private String turn;

    @Column(length = 10)
    private String result;

    @Column(name = "end_reason", length = 30)
    private String endReason;

    @Column(name = "time_control_initial_ms")
    private Long timeControlInitialMs;

    @Column(name = "time_control_increment_ms")
    private Long timeControlIncrementMs;

    @Column(name = "clock_white_ms")
    private Long clockWhiteMs;

    @Column(name = "clock_black_ms")
    private Long clockBlackMs;

    @Column(name = "white_elo_before")
    private Integer whiteEloBefore;

    @Column(name = "black_elo_before")
    private Integer blackEloBefore;

    @Column(name = "finalized_game_id")
    private Long finalizedGameId;

    /** Si la partida proviene de un emparejamiento de torneo, su id (para
     *  registrar el resultado de vuelta en ms-tournament al finalizar). */
    @Column(name = "tournament_pairing_id")
    private Long tournamentPairingId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "last_move_at")
    private Instant lastMoveAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public enum SessionStatus { WAITING, ACTIVE, FINISHED, ABANDONED }
}
