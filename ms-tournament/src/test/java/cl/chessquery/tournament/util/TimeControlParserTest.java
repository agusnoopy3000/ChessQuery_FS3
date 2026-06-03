package cl.chessquery.tournament.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimeControlParserTest {

    @Test
    void parseaBaseMasIncremento() {
        var tc = TimeControlParser.parse("90+30");
        assertThat(tc.initialMs()).isEqualTo(90 * 60_000L);
        assertThat(tc.incrementMs()).isEqualTo(30_000L);
    }

    @Test
    void toleraTextoYEspacios() {
        var tc = TimeControlParser.parse("Rápido 15 + 10");
        assertThat(tc.initialMs()).isEqualTo(15 * 60_000L);
        assertThat(tc.incrementMs()).isEqualTo(10_000L);
    }

    @Test
    void unSoloNumeroSeInterpretaComoMinutos() {
        var tc = TimeControlParser.parse("5");
        assertThat(tc.initialMs()).isEqualTo(5 * 60_000L);
        assertThat(tc.incrementMs()).isZero();
    }

    @Test
    void nullOBlankCaeAlDefault() {
        var def = TimeControlParser.parse(null);
        assertThat(def.initialMs()).isEqualTo(TimeControlParser.DEFAULT_INITIAL_MS);
        assertThat(def.incrementMs()).isEqualTo(TimeControlParser.DEFAULT_INCREMENT_MS);
        assertThat(TimeControlParser.parse("   ").initialMs()).isEqualTo(TimeControlParser.DEFAULT_INITIAL_MS);
    }

    @Test
    void textoSinNumerosCaeAlDefault() {
        var tc = TimeControlParser.parse("a definir");
        assertThat(tc.initialMs()).isEqualTo(TimeControlParser.DEFAULT_INITIAL_MS);
    }
}
