package cl.chessquery.game.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "live_game_move")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class LiveGameMove {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "move_number", nullable = false)
    private Integer moveNumber;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(nullable = false, length = 1)
    private String color;

    @Column(nullable = false, length = 10)
    private String uci;

    @Column(nullable = false, length = 15)
    private String san;

    @Column(name = "fen_after", nullable = false, length = 120)
    private String fenAfter;

    @Column(name = "clock_white_ms")
    private Long clockWhiteMs;

    @Column(name = "clock_black_ms")
    private Long clockBlackMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
