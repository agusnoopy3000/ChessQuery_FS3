package cl.chessquery.tournament.pairing;

import java.util.List;

public interface PairingStrategy {
    List<PairingResult> generatePairings(List<PlayerStanding> standings, int roundNumber);
}
