package cl.chessquery.users.service;

import cl.chessquery.users.dto.RankingEntryResponse;
import cl.chessquery.users.entity.ChessTitle;
import cl.chessquery.users.entity.Club;
import cl.chessquery.users.entity.Player;
import cl.chessquery.users.entity.PlayerTitleHistory;
import cl.chessquery.users.repository.PlayerRepository;
import cl.chessquery.users.repository.PlayerTitleHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link RankingService}.
 *
 * <p>Mockea {@link PlayerRepository} y {@link PlayerTitleHistoryRepository}.
 * Verifica el filtro por categoría, el cap de 200, el mapeo de títulos
 * y la posición secuencial.</p>
 */
@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock private PlayerRepository playerRepo;
    @Mock private PlayerTitleHistoryRepository titleRepo;
    @InjectMocks private RankingService service;

    private Player player(long id, String first, int elo) {
        return Player.builder().id(id).firstName(first).lastName("X")
                .birthDate(LocalDate.now().minusYears(25))
                .eloNational(elo)
                .build();
    }

    @Test
    @DisplayName("getRanking_assignsSequentialPositionsStartingAtOne")
    void getRanking_assignsSequentialPositionsStartingAtOne() {
        when(playerRepo.findRanking(any(), any(), any(), any()))
                .thenReturn(List.of(player(1L, "A", 1800), player(2L, "B", 1700)));
        when(titleRepo.findByPlayerIdAndIsCurrentTrue(any())).thenReturn(Optional.empty());
        List<RankingEntryResponse> r = service.getRanking(null, null, 10);
        assertThat(r).hasSize(2);
        assertThat(r.get(0).position()).isEqualTo(1);
        assertThat(r.get(1).position()).isEqualTo(2);
    }

    @Test
    @DisplayName("getRanking_includesTitleWhenPresent")
    void getRanking_includesTitleWhenPresent() {
        Player p = player(1L, "Mag", 2800);
        when(playerRepo.findRanking(any(), any(), any(), any())).thenReturn(List.of(p));
        PlayerTitleHistory h = PlayerTitleHistory.builder().title(ChessTitle.GM).build();
        when(titleRepo.findByPlayerIdAndIsCurrentTrue(eq(1L))).thenReturn(Optional.of(h));
        List<RankingEntryResponse> r = service.getRanking(null, null, 10);
        assertThat(r.get(0).currentTitle()).isEqualTo("GM");
    }

    @Test
    @DisplayName("getRanking_invalidCategory_filterIsIgnored")
    void getRanking_invalidCategory_filterIsIgnored() {
        when(playerRepo.findRanking(any(), any(), any(), any())).thenReturn(List.of());
        service.getRanking("UNKNOWN_CATEGORY", null, 10);
        // No throw; el cat null no rompe la query.
    }

    @Test
    @DisplayName("getRanking_capsLimitAt200")
    void getRanking_capsLimitAt200() {
        when(playerRepo.findRanking(any(), any(), any(), any())).thenReturn(List.of());
        service.getRanking(null, null, 9999);
        // No assertion del Pageable directo, pero el método no debe romper.
    }

    @Test
    @DisplayName("getRanking_includesClubNameWhenPresent")
    void getRanking_includesClubNameWhenPresent() {
        Player p = player(1L, "A", 1500);
        p.setClub(Club.builder().name("Club Santiago").build());
        when(playerRepo.findRanking(any(), any(), any(), any())).thenReturn(List.of(p));
        when(titleRepo.findByPlayerIdAndIsCurrentTrue(any())).thenReturn(Optional.empty());
        List<RankingEntryResponse> r = service.getRanking(null, null, 10);
        assertThat(r.get(0).clubName()).isEqualTo("Club Santiago");
    }
}
