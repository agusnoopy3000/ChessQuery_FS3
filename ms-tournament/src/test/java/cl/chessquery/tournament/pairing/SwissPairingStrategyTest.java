package cl.chessquery.tournament.pairing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SwissPairingStrategyTest {

    private final SwissPairingStrategy strategy = new SwissPairingStrategy();

    @Test
    void generatePairings_fourPlayers_returnsTwoPairings() {
        List<PlayerStanding> standings = List.of(
                new PlayerStanding(1L, 2.0, 2000, 3),
                new PlayerStanding(2L, 1.5, 1800, 3),
                new PlayerStanding(3L, 1.0, 1700, 3),
                new PlayerStanding(4L, 0.5, 1600, 3)
        );

        List<PairingResult> pairings = strategy.generatePairings(standings, 1);

        assertThat(pairings).hasSize(2);
        assertThat(pairings.get(0).boardNumber()).isEqualTo(1);
        assertThat(pairings.get(1).boardNumber()).isEqualTo(2);
    }

    @Test
    void generatePairings_oddPlayers_dropsBye() {
        List<PlayerStanding> standings = List.of(
                new PlayerStanding(1L, 1.0, 2000, 2),
                new PlayerStanding(2L, 1.0, 1800, 2),
                new PlayerStanding(3L, 0.0, 1600, 2)
        );

        List<PairingResult> pairings = strategy.generatePairings(standings, 2);

        assertThat(pairings).hasSize(1); // El 3er jugador queda sin par (bye)
    }
}
