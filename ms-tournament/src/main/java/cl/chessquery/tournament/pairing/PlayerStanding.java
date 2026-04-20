package cl.chessquery.tournament.pairing;

public record PlayerStanding(Long playerId, double points, int seedRating, int gamesPlayed) {}
