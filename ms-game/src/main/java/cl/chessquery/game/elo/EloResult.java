package cl.chessquery.game.elo;

public record EloResult(int whiteNewElo, int blackNewElo, int whiteDelta, int blackDelta) {}
