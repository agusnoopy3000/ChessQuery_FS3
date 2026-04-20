package cl.chessquery.tournament.dto;

public record StandingEntry(
        int position,
        Long playerId,
        double points,
        double buchholz,
        double sonnebornBerger,
        int seedRating
) {}
