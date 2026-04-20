package cl.chessquery.game.elo;

import org.springframework.stereotype.Service;

/**
 * Calcula los nuevos valores ELO usando la fórmula estándar FIDE.
 * K=32 para jugadores con menos de 30 partidas, K=16 en caso contrario.
 */
@Service
public class EloCalculator {

    /**
     * @param whiteElo         ELO actual del jugador blanco
     * @param blackElo         ELO actual del jugador negro
     * @param result           Resultado: "1-0", "0-1" o "1/2-1/2"
     * @param whiteTotalGames  Número de partidas previas del jugador blanco (para K factor)
     * @param blackTotalGames  Número de partidas previas del jugador negro (para K factor)
     */
    public EloResult calculate(int whiteElo, int blackElo, String result,
                               int whiteTotalGames, int blackTotalGames) {
        double scoreWhite = switch (result) {
            case "1-0"     -> 1.0;
            case "0-1"     -> 0.0;
            case "1/2-1/2" -> 0.5;
            default -> throw new IllegalArgumentException("Resultado inválido: " + result);
        };
        double scoreBlack = 1.0 - scoreWhite;

        double expectedWhite = 1.0 / (1 + Math.pow(10, (blackElo - whiteElo) / 400.0));
        double expectedBlack = 1.0 - expectedWhite;

        int kWhite = whiteTotalGames < 30 ? 32 : 16;
        int kBlack = blackTotalGames < 30 ? 32 : 16;

        int newWhite = (int) Math.round(whiteElo + kWhite * (scoreWhite - expectedWhite));
        int newBlack = (int) Math.round(blackElo + kBlack * (scoreBlack - expectedBlack));

        return new EloResult(newWhite, newBlack, newWhite - whiteElo, newBlack - blackElo);
    }
}
