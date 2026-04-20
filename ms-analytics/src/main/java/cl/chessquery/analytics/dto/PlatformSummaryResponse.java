package cl.chessquery.analytics.dto;

public record PlatformSummaryResponse(
        long totalPlayers,
        long totalGames,
        long activeTournaments
) {}
