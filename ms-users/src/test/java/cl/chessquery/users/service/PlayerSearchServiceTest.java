package cl.chessquery.users.service;

import cl.chessquery.users.entity.*;
import cl.chessquery.users.exception.ApiException;
import cl.chessquery.users.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayerSearchServiceTest {

    @Mock PlayerRepository            playerRepo;
    @Mock ClubRepository              clubRepo;
    @Mock RatingHistoryRepository     historyRepo;
    @Mock PlayerTitleHistoryRepository titleRepo;
    @Mock EventPublisherService       events;

    @InjectMocks PlayerService playerService;

    private Player samplePlayer;

    @BeforeEach
    void setUp() {
        Country country = Country.builder().id(1).isoCode("CHL").name("Chile").build();
        samplePlayer = Player.builder()
                .id(4L)
                .firstName("Rodrigo")
                .lastName("Sepúlveda")
                .email("rodrigo@demo.cl")
                .fideId("3600001")
                .country(country)
                .eloNational(2100)
                .eloFideStandard(2050)
                .build();
    }

    @Test
    void search_validQuery_returnsResults() {
        when(playerRepo.searchFuzzy("Rodrigo", 20)).thenReturn(List.of(samplePlayer));
        when(titleRepo.findByPlayerIdAndIsCurrentTrue(4L)).thenReturn(Optional.of(
                PlayerTitleHistory.builder()
                        .player(samplePlayer).title(ChessTitle.FM).isCurrent(true)
                        .build()
        ));

        var results = playerService.search("Rodrigo", 20);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).firstName()).isEqualTo("Rodrigo");
        assertThat(results.get(0).currentTitle()).isEqualTo("FM");
        assertThat(results.get(0).eloNational()).isEqualTo(2100);
        verify(playerRepo).searchFuzzy("Rodrigo", 20);
    }

    @Test
    void search_byFideId_returnsPlayer() {
        when(playerRepo.searchFuzzy("3600001", 10)).thenReturn(List.of(samplePlayer));
        when(titleRepo.findByPlayerIdAndIsCurrentTrue(any())).thenReturn(Optional.empty());

        var results = playerService.search("3600001", 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).fideId()).isEqualTo("3600001");
    }

    @Test
    void search_emptyQuery_throwsBadRequest() {
        assertThatThrownBy(() -> playerService.search("", 20))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(400));
        verifyNoInteractions(playerRepo);
    }

    @Test
    void search_blankQuery_throwsBadRequest() {
        assertThatThrownBy(() -> playerService.search("   ", 20))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getError()).isEqualTo("INVALID_QUERY"));
    }

    @Test
    void search_limitCappedAt50() {
        when(playerRepo.searchFuzzy(anyString(), eq(50))).thenReturn(List.of());
        when(playerRepo.searchFuzzy(anyString(), eq(100))).thenReturn(List.of());

        playerService.search("test", 200);  // pide 200, debe ejecutarse con 50
        verify(playerRepo).searchFuzzy("test", 50);
    }

    @Test
    void search_emptyResult_returnsEmptyList() {
        when(playerRepo.searchFuzzy("xXyYzZ", 20)).thenReturn(List.of());

        var results = playerService.search("xXyYzZ", 20);

        assertThat(results).isEmpty();
    }

    @Test
    void getProfile_nonExistentPlayer_throws404() {
        when(playerRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playerService.getProfile(999L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(404));
    }
}
