package cl.chessquery.analytics.dto;

public record OpeningStatsEntry(
        Integer openingId,
        long totalGames,
        long wins
) {}
