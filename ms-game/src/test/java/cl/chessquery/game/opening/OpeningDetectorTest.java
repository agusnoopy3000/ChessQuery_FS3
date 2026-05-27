package cl.chessquery.game.opening;

import cl.chessquery.game.entity.Opening;
import cl.chessquery.game.repository.OpeningRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link OpeningDetector}.
 *
 * <p>Mockea {@link OpeningRepository} para verificar que la extracción de
 * movimientos del PGN sigue las reglas: ignora cabeceras [Tag "Val"],
 * elimina números de turno, ignora comentarios {}, y corta en el resultado.</p>
 */
@ExtendWith(MockitoExtension.class)
class OpeningDetectorTest {

    @Mock private OpeningRepository openingRepo;
    @InjectMocks private OpeningDetector detector;

    @Test
    @DisplayName("detectOpening_nullPgn_returnsEmpty")
    void detectOpening_nullPgn_returnsEmpty() {
        assertThat(detector.detectOpening(null)).isEmpty();
        verify(openingRepo, never()).findBestMatch(anyString());
    }

    @Test
    @DisplayName("detectOpening_blankPgn_returnsEmpty")
    void detectOpening_blankPgn_returnsEmpty() {
        assertThat(detector.detectOpening("   ")).isEmpty();
        verify(openingRepo, never()).findBestMatch(anyString());
    }

    @Test
    @DisplayName("detectOpening_onlyHeaders_returnsEmpty")
    void detectOpening_onlyHeaders_returnsEmpty() {
        String pgn = "[Event \"X\"]\n[Site \"Y\"]\n";
        assertThat(detector.detectOpening(pgn)).isEmpty();
        verify(openingRepo, never()).findBestMatch(anyString());
    }

    @Test
    @DisplayName("detectOpening_validPgn_callsRepoWithCleanedMoves")
    void detectOpening_validPgn_callsRepoWithCleanedMoves() {
        Opening opening = Opening.builder().id(1).ecoCode("C20").name("KP").build();
        when(openingRepo.findBestMatch(anyString())).thenReturn(Optional.of(opening));

        String pgn = "[Event \"Test\"]\n\n1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 1-0";
        Optional<Opening> result = detector.detectOpening(pgn);

        assertThat(result).contains(opening);
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(openingRepo).findBestMatch(cap.capture());
        assertThat(cap.getValue())
                .doesNotContain("[")
                .doesNotContain("1.")
                .doesNotContain("1-0")
                .contains("e4", "e5", "Nf3", "Nc6", "Bb5", "a6");
    }

    @Test
    @DisplayName("detectOpening_pgnWithComments_stripsCommentsBeforeMatch")
    void detectOpening_pgnWithComments_stripsCommentsBeforeMatch() {
        when(openingRepo.findBestMatch(anyString())).thenReturn(Optional.empty());

        String pgn = "1. e4 {un comentario} e5 2. Nf3 Nc6";
        detector.detectOpening(pgn);

        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(openingRepo).findBestMatch(cap.capture());
        assertThat(cap.getValue()).doesNotContain("comentario").doesNotContain("{");
    }

    @Test
    @DisplayName("detectOpening_truncatesToTenPairs")
    void detectOpening_truncatesToTenPairs() {
        when(openingRepo.findBestMatch(anyString())).thenReturn(Optional.empty());

        StringBuilder pgn = new StringBuilder();
        for (int i = 1; i <= 15; i++) {
            pgn.append(i).append(". e4 e5 ");
        }
        detector.detectOpening(pgn.toString());

        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(openingRepo).findBestMatch(cap.capture());
        long tokenCount = cap.getValue().split("\\s+").length;
        assertThat(tokenCount).isLessThanOrEqualTo(20);
    }

    @Test
    @DisplayName("detectOpening_repoThrows_returnsEmptyWithoutPropagating")
    void detectOpening_repoThrows_returnsEmptyWithoutPropagating() {
        when(openingRepo.findBestMatch(anyString())).thenThrow(new RuntimeException("DB down"));
        assertThat(detector.detectOpening("1. e4 e5")).isEmpty();
    }

    @Test
    @DisplayName("detectOpening_drawResultMarker_stopsExtraction")
    void detectOpening_drawResultMarker_stopsExtraction() {
        when(openingRepo.findBestMatch(anyString())).thenReturn(Optional.empty());

        detector.detectOpening("1. e4 e5 1/2-1/2");

        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(openingRepo).findBestMatch(cap.capture());
        assertThat(cap.getValue()).doesNotContain("1/2-1/2");
    }
}
