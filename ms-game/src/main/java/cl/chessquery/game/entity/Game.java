package cl.chessquery.game.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "game")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "white_player_id", nullable = false)
    private Long whitePlayerId;

    @Column(name = "black_player_id", nullable = false)
    private Long blackPlayerId;

    @Column(name = "result", length = 10, nullable = false)
    private String result;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_type", nullable = false)
    private GameType gameType;

    @Column(name = "white_elo_before")
    private Integer whiteEloBefore;

    @Column(name = "black_elo_before")
    private Integer blackEloBefore;

    @Column(name = "white_elo_after")
    private Integer whiteEloAfter;

    @Column(name = "black_elo_after")
    private Integer blackEloAfter;

    @Column(name = "total_moves")
    private Integer totalMoves;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opening_id")
    private Opening opening;

    @Column(name = "pgn_storage_key", length = 500)
    private String pgnStorageKey;

    @Column(name = "tournament_pairing_id")
    private Long tournamentPairingId;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "played_at", nullable = false)
    private Instant playedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (this.playedAt == null) {
            this.playedAt = Instant.now();
        }
    }
}
