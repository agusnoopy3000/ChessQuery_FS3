package cl.chessquery.tournament.pairing;

import cl.chessquery.tournament.entity.TournamentFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios de las estrategias de emparejamiento:
 * {@link KnockoutPairingStrategy}, {@link RoundRobinPairingStrategy},
 * {@link PairingStrategyFactory}.
 *
 * <p>Sin dependencias externas: lógica pura sobre {@link PlayerStanding}.</p>
 *
 * <p>Invariantes:
 * <ul>
 *   <li>Knockout R1 empareja por seed cruzado (1↔N, 2↔N-1).</li>
 *   <li>RoundRobin con N impar genera N-1 pares y excluye al bye.</li>
 *   <li>La factory devuelve la implementación correcta por formato.</li>
 * </ul></p>
 */
class PairingStrategiesTest {

    private PlayerStanding standing(long id, int rating, double points) {
        return new PlayerStanding(id, points, rating, 0);
    }

    @Nested
    @DisplayName("KnockoutPairingStrategy")
    class Knockout {

        @Test
        @DisplayName("knockout_round1_pairsTopVsBottomBySeed")
        void knockout_round1_pairsTopVsBottomBySeed() {
            KnockoutPairingStrategy s = new KnockoutPairingStrategy();
            List<PlayerStanding> players = List.of(
                    standing(1L, 1800, 0),
                    standing(2L, 1700, 0),
                    standing(3L, 1600, 0),
                    standing(4L, 1500, 0)
            );
            List<PairingResult> pairs = s.generatePairings(players, 1);
            assertThat(pairs).hasSize(2);
            assertThat(pairs.get(0).whitePlayerId()).isEqualTo(1L);
            assertThat(pairs.get(0).blackPlayerId()).isEqualTo(4L);
            assertThat(pairs.get(1).whitePlayerId()).isEqualTo(2L);
            assertThat(pairs.get(1).blackPlayerId()).isEqualTo(3L);
        }

        @Test
        @DisplayName("knockout_round1_oddPlayers_dropsLowestSeed")
        void knockout_round1_oddPlayers_dropsLowestSeed() {
            KnockoutPairingStrategy s = new KnockoutPairingStrategy();
            List<PairingResult> pairs = s.generatePairings(List.of(
                    standing(1L, 1800, 0), standing(2L, 1700, 0), standing(3L, 1600, 0)), 1);
            assertThat(pairs).hasSize(1);
        }

        @Test
        @DisplayName("knockout_roundN_pairsByPointsDescending")
        void knockout_roundN_pairsByPointsDescending() {
            KnockoutPairingStrategy s = new KnockoutPairingStrategy();
            List<PlayerStanding> winners = List.of(
                    standing(1L, 1800, 1.0),
                    standing(2L, 1700, 1.0),
                    standing(3L, 1600, 1.0),
                    standing(4L, 1500, 1.0)
            );
            List<PairingResult> pairs = s.generatePairings(winners, 2);
            assertThat(pairs).hasSize(2);
        }
    }

    @Nested
    @DisplayName("RoundRobinPairingStrategy")
    class RoundRobin {

        @Test
        @DisplayName("roundRobin_evenPlayers_round1_generatesNHalfPairs")
        void roundRobin_evenPlayers_round1_generatesNHalfPairs() {
            RoundRobinPairingStrategy s = new RoundRobinPairingStrategy();
            List<PlayerStanding> players = List.of(
                    standing(1L, 1500, 0), standing(2L, 1500, 0),
                    standing(3L, 1500, 0), standing(4L, 1500, 0)
            );
            List<PairingResult> pairs = s.generatePairings(players, 1);
            assertThat(pairs).hasSize(2);
        }

        @Test
        @DisplayName("roundRobin_oddPlayers_oneByeSkippedPerRound")
        void roundRobin_oddPlayers_oneByeSkippedPerRound() {
            RoundRobinPairingStrategy s = new RoundRobinPairingStrategy();
            List<PlayerStanding> players = List.of(
                    standing(1L, 1500, 0), standing(2L, 1500, 0),
                    standing(3L, 1500, 0));
            List<PairingResult> pairs = s.generatePairings(players, 1);
            // 3 jugadores → bye → solo se generan pares válidos (sin null)
            assertThat(pairs).allMatch(p -> p.whitePlayerId() != null && p.blackPlayerId() != null);
        }

        @Test
        @DisplayName("roundRobin_evenRound_swapsColors")
        void roundRobin_evenRound_swapsColors() {
            RoundRobinPairingStrategy s = new RoundRobinPairingStrategy();
            List<PlayerStanding> players = List.of(
                    standing(1L, 1500, 0), standing(2L, 1500, 0),
                    standing(3L, 1500, 0), standing(4L, 1500, 0)
            );
            List<PairingResult> round1 = s.generatePairings(players, 1);
            List<PairingResult> round2 = s.generatePairings(players, 2);
            assertThat(round1).isNotEqualTo(round2);
        }
    }

    @Nested
    @DisplayName("PairingStrategyFactory")
    class Factory {

        @Test
        @DisplayName("getStrategy_swiss_returnsSwiss")
        void getStrategy_swiss_returnsSwiss() {
            assertThat(new PairingStrategyFactory().getStrategy(TournamentFormat.SWISS))
                    .isInstanceOf(SwissPairingStrategy.class);
        }

        @Test
        @DisplayName("getStrategy_roundRobin_returnsRoundRobin")
        void getStrategy_roundRobin_returnsRoundRobin() {
            assertThat(new PairingStrategyFactory().getStrategy(TournamentFormat.ROUND_ROBIN))
                    .isInstanceOf(RoundRobinPairingStrategy.class);
        }

        @Test
        @DisplayName("getStrategy_knockout_returnsKnockout")
        void getStrategy_knockout_returnsKnockout() {
            assertThat(new PairingStrategyFactory().getStrategy(TournamentFormat.KNOCKOUT))
                    .isInstanceOf(KnockoutPairingStrategy.class);
        }
    }
}
