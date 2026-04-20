package cl.chessquery.analytics.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "player_stats_mv")
public class PlayerStatsMV {

    /** El ID es el mismo ID del jugador (no auto-generado). */
    @Id
    @Column(name = "player_id")
    private Long playerId;

    @Column(name = "total_games", nullable = false)
    @Builder.Default
    private int totalGames = 0;

    @Column(name = "wins", nullable = false)
    @Builder.Default
    private int wins = 0;

    @Column(name = "losses", nullable = false)
    @Builder.Default
    private int losses = 0;

    @Column(name = "draws", nullable = false)
    @Builder.Default
    private int draws = 0;

    @Column(name = "win_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal winRate = BigDecimal.ZERO;

    @Column(name = "avg_moves", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal avgMoves = BigDecimal.ZERO;

    /** Positivo = victorias consecutivas, negativo = derrotas consecutivas. */
    @Column(name = "current_streak", nullable = false)
    @Builder.Default
    private int currentStreak = 0;

    @Column(name = "best_elo", nullable = false)
    @Builder.Default
    private int bestElo = 0;

    @Column(name = "last_refreshed", nullable = false)
    private Instant lastRefreshed;
}
