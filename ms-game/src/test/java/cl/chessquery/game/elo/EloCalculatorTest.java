package cl.chessquery.game.elo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EloCalculatorTest {

    private final EloCalculator calculator = new EloCalculator();

    @Test
    void calculate_whiteWins_whiteGainsElo() {
        EloResult result = calculator.calculate(1500, 1500, "1-0", 0, 0);

        assertThat(result.whiteDelta()).isGreaterThan(0);
        assertThat(result.blackDelta()).isLessThan(0);
        assertThat(result.whiteNewElo()).isEqualTo(1516); // K=32, expected=0.5, score=1 → +16
        assertThat(result.blackNewElo()).isEqualTo(1484);
    }

    @Test
    void calculate_draw_equalPlayers_noChange() {
        EloResult result = calculator.calculate(1500, 1500, "1/2-1/2", 0, 0);

        assertThat(result.whiteDelta()).isEqualTo(0);
        assertThat(result.blackDelta()).isEqualTo(0);
    }

    @Test
    void calculate_weakerPlayerWins_bigGain() {
        // Jugador débil (1200) vence a jugador fuerte (1800)
        EloResult result = calculator.calculate(1200, 1800, "1-0", 0, 0);

        // El jugador débil gana mucho
        assertThat(result.whiteDelta()).isGreaterThan(20);
        // El jugador fuerte pierde mucho
        assertThat(result.blackDelta()).isLessThan(-20);
    }

    @Test
    void calculate_kFactor16_forExperiencedPlayer() {
        // Jugador con más de 30 partidas → K=16
        EloResult result = calculator.calculate(1500, 1500, "1-0", 30, 0);

        // Con K=16: ganancia = 16 * (1 - 0.5) = 8
        assertThat(result.whiteNewElo()).isEqualTo(1508);
    }
}
