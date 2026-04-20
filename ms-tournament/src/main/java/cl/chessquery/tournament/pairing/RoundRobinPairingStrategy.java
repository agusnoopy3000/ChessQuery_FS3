package cl.chessquery.tournament.pairing;

import java.util.ArrayList;
import java.util.List;

/**
 * Round Robin con algoritmo de rotación Berger.
 * Fija el jugador en la posición 0 y rota los restantes.
 * Si el número de jugadores es impar se agrega un jugador ficticio (bye).
 */
public class RoundRobinPairingStrategy implements PairingStrategy {

    @Override
    public List<PairingResult> generatePairings(List<PlayerStanding> standings, int roundNumber) {
        List<Long> playerIds = standings.stream()
                .sorted((a, b) -> Integer.compare(b.seedRating(), a.seedRating()))
                .map(PlayerStanding::playerId)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        boolean hasBye = playerIds.size() % 2 != 0;
        if (hasBye) {
            playerIds.add(null); // null representa el bye
        }

        int n = playerIds.size();
        // Construir tabla de rotación para la ronda indicada
        // La ronda 1 corresponde al índice 0 de rotaciones
        List<Long> rotated = new ArrayList<>(playerIds);
        Long fixed = rotated.get(0);

        // Rotar (roundNumber - 1) veces los elementos del índice 1 en adelante
        for (int r = 0; r < (roundNumber - 1); r++) {
            Long last = rotated.remove(rotated.size() - 1);
            rotated.add(1, last);
        }

        List<PairingResult> pairings = new ArrayList<>();
        int board = 1;

        for (int i = 0; i < n / 2; i++) {
            Long white = rotated.get(i);
            Long black = rotated.get(n - 1 - i);

            // Si alguno es null → bye, no creamos pairing
            if (white == null || black == null) {
                continue;
            }

            // Alternar colores por ronda
            if (roundNumber % 2 == 0) {
                Long tmp = white;
                white = black;
                black = tmp;
            }
            pairings.add(new PairingResult(white, black, board++));
        }

        return pairings;
    }
}
