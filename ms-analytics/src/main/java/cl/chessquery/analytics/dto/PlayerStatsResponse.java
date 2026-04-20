package cl.chessquery.analytics.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PlayerStatsResponse(
        Long playerId,
        int totalGames,
        int wins,
        int losses,
        int draws,
        BigDecimal winRate,
        BigDecimal avgMoves,
        int currentStreak,
        int bestElo,
        Instant lastRefreshed
) {}
