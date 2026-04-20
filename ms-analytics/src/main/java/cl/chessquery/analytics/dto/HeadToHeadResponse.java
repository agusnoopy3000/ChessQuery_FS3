package cl.chessquery.analytics.dto;

public record HeadToHeadResponse(
        Long playerId,
        Long opponentId,
        int totalGames,
        int wins,
        int losses,
        int draws
) {}
