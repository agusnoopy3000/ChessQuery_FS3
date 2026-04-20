package cl.chessquery.tournament.pairing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sistema suizo: ordena por puntos DESC, rating DESC.
 * Empareja consecutivos (0-1, 2-3, …).
 * Si número impar de jugadores, el último queda sin par (bye — ignorado).
 * Alterna colores basado en roundNumber % 2.
 */
public class SwissPairingStrategy implements PairingStrategy {

    @Override
    public List<PairingResult> generatePairings(List<PlayerStanding> standings, int roundNumber) {
        List<PlayerStanding> sorted = standings.stream()
                .sorted(Comparator.comparingDouble(PlayerStanding::points).reversed()
                        .thenComparingInt(PlayerStanding::seedRating).reversed())
                .toList();

        List<PairingResult> pairings = new ArrayList<>();
        int board = 1;

        for (int i = 0; i + 1 < sorted.size(); i += 2) {
            PlayerStanding a = sorted.get(i);
            PlayerStanding b = sorted.get(i + 1);

            Long white;
            Long black;
            // Alternamos quién va con blancas según la ronda
            if (roundNumber % 2 == 1) {
                white = a.playerId();
                black = b.playerId();
            } else {
                white = b.playerId();
                black = a.playerId();
            }
            pairings.add(new PairingResult(white, black, board++));
        }
        // Si size es impar, el último jugador queda libre (bye — no se añade pairing)
        return pairings;
    }
}
