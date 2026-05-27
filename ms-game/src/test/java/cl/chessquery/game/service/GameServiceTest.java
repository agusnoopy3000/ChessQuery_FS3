package cl.chessquery.game.service;

import cl.chessquery.game.dto.GameResponse;
import cl.chessquery.game.dto.RegisterGameRequest;
import cl.chessquery.game.elo.EloCalculator;
import cl.chessquery.game.elo.EloResult;
import cl.chessquery.game.entity.Game;
import cl.chessquery.game.entity.GameType;
import cl.chessquery.game.exception.ApiException;
import cl.chessquery.game.opening.OpeningDetector;
import cl.chessquery.game.repository.GameRepository;
import cl.chessquery.game.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock private GameRepository gameRepo;
    @Mock private EloCalculator eloCalculator;
    @Mock private OpeningDetector openingDetector;
    @Mock private StorageService storageService;
    @Mock private EventPublisherService events;

    @InjectMocks
    private GameService gameService;

    private RegisterGameRequest baseRequest(String result) {
        return new RegisterGameRequest(
                1L, 2L, result, GameType.CASUAL,
                1500, 1500, 40, null, 600, null, null
        );
    }

    @Test
    void registerGame_whiteWins_persistsAndPublishesGameFinishedAndEloEvents() {
        RegisterGameRequest req = baseRequest("1-0");
        when(eloCalculator.calculate(eq(1500), eq(1500), eq("1-0"), anyInt(), anyInt()))
                .thenReturn(new EloResult(1516, 1484, 16, -16));
        when(gameRepo.save(any(Game.class))).thenAnswer(inv -> {
            Game g = inv.getArgument(0);
            g.setId(42L);
            return g;
        });

        GameResponse resp = gameService.registerGame(req);

        assertThat(resp).isNotNull();
        assertThat(resp.id()).isEqualTo(42L);
        assertThat(resp.result()).isEqualTo("1-0");
        assertThat(resp.whiteEloAfter()).isEqualTo(1516);
        assertThat(resp.blackEloAfter()).isEqualTo(1484);
        verify(events, times(1)).publishGameFinished(any(Game.class));
        verify(events, times(1)).publishEloUpdated(eq(1L), eq(1500), eq(1516), eq(42L), eq("NATIONAL"));
        verify(events, times(1)).publishEloUpdated(eq(2L), eq(1500), eq(1484), eq(42L), eq("NATIONAL"));
    }

    @Test
    void registerGame_withoutPgn_doesNotCallStorage() {
        RegisterGameRequest req = baseRequest("0-1");
        when(eloCalculator.calculate(anyInt(), anyInt(), anyString(), anyInt(), anyInt()))
                .thenReturn(new EloResult(1490, 1510, -10, 10));
        when(gameRepo.save(any(Game.class))).thenAnswer(inv -> {
            Game g = inv.getArgument(0);
            g.setId(7L);
            return g;
        });

        gameService.registerGame(req);

        verify(storageService, never()).uploadPgn(anyString(), any(byte[].class));
    }

    @Test
    void registerGame_storageFailure_throwsServiceUnavailable() {
        RegisterGameRequest req = new RegisterGameRequest(
                1L, 2L, "1/2-1/2", GameType.CASUAL,
                1500, 1500, 40, null, 600, null, "1. e4 e5"
        );
        when(eloCalculator.calculate(anyInt(), anyInt(), anyString(), anyInt(), anyInt()))
                .thenReturn(new EloResult(1500, 1500, 0, 0));
        when(openingDetector.detectOpening(anyString())).thenReturn(Optional.empty());
        when(gameRepo.save(any(Game.class))).thenAnswer(inv -> {
            Game g = inv.getArgument(0);
            g.setId(99L);
            return g;
        });
        doThrow(new RuntimeException("S3 caído"))
                .when(storageService).uploadPgn(anyString(), any(byte[].class));

        assertThatThrownBy(() -> gameService.registerGame(req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("PGN");
    }
}
