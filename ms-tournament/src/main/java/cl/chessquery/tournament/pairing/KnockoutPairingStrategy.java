package cl.chessquery.tournament.pairing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Eliminación directa (Knockout).
 * Ronda 1: empareja por seed (1 vs N, 2 vs N-1, …).
 * Rondas siguientes: empareja los ganadores secuencialmente
 * (se asume que los standings ya reflejan solo los jugadores activos con puntos).
 */
public class KnockoutPairingStrategy implements PairingStrategy {

    @Override
    public List<PairingResult> generatePairings(List<PlayerStanding> standings, int roundNumber) {
        List<PlayerStanding> sorted = standings.stream()
                .sorted(Comparator.comparingInt(PlayerStanding::seedRating).reversed())
                .toList();

        List<PairingResult> pairings = new ArrayList<>();
        int board = 1;

        if (roundNumber == 1) {
            // 1 vs N, 2 vs N-1, ...
            int n = sorted.size();
            for (int i = 0; i < n / 2; i++) {
                Long white = sorted.get(i).playerId();
                Long black  = sorted.get(n - 1 - i).playerId();
                pairings.add(new PairingResult(white, black, board++));
            }
        } else {
            // Rondas posteriores: emparejar ganadores (lista ya filtrada por el servicio)
            // Empareja consecutivos del listado ordenado por puntos DESC
            List<PlayerStanding> byPoints = standings.stream()
                    .sorted(Comparator.comparingDouble(PlayerStanding::points).reversed()
                            .thenComparingInt(PlayerStanding::seedRating).reversed())
                    .toList();

            for (int i = 0; i + 1 < byPoints.size(); i += 2) {
                Long white = byPoints.get(i).playerId();
                Long black  = byPoints.get(i + 1).playerId();
                pairings.add(new PairingResult(white, black, board++));
            }
        }

        return pairings;
    }
}
