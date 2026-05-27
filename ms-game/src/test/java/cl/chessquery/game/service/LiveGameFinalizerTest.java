package cl.chessquery.game.service;

import cl.chessquery.game.dto.GameResponse;
import cl.chessquery.game.dto.RegisterGameRequest;
import cl.chessquery.game.entity.GameType;
import cl.chessquery.game.entity.LiveGameSession;
import cl.chessquery.game.realtime.LiveGameBroadcaster;
import cl.chessquery.game.repository.LiveGameSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link LiveGameFinalizer}.
 *
 * <p>El finalizer usa un ExecutorService propio y un TransactionTemplate.
 * Mockeamos {@link PlatformTransactionManager} para evitar contexto Spring
 * y forzamos al template a ejecutar el callback inmediatamente.</p>
 *
 * <p>Invariantes:
 * <ul>
 *   <li>Si la sesión ya tiene finalizedGameId, no se vuelve a registrar.</li>
 *   <li>Ante fallos transitorios reintenta hasta 3 veces.</li>
 *   <li>Tras éxito, broadcastea game.finished con el finalizedGameId.</li>
 * </ul></p>
 */
@ExtendWith(MockitoExtension.class)
class LiveGameFinalizerTest {

    @Mock private LiveGameSessionRepository sessionRepo;
    @Mock private GameService gameService;
    @Mock private LiveGameBroadcaster broadcaster;
    @Mock private PlatformTransactionManager txManager;

    private LiveGameFinalizer finalizer;

    private RegisterGameRequest req() {
        return new RegisterGameRequest(1L, 2L, "1-0", GameType.CASUAL,
                1500, 1500, 5, null, 30, Instant.now(), "1. e4 e5 1-0");
    }

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(txManager.getTransaction(any()))
                .thenReturn(new SimpleTransactionStatus());
        finalizer = new LiveGameFinalizer(sessionRepo, gameService, broadcaster, txManager);
    }

    @Test
    @DisplayName("scheduleAfterCommit_noActiveTransaction_runsImmediatelyAndPersists")
    void scheduleAfterCommit_noActiveTransaction_runsImmediatelyAndPersists() {
        LiveGameSession s = LiveGameSession.builder().build();
        s.setId(10L);
        when(sessionRepo.findById(eq(10L))).thenReturn(Optional.of(s));
        when(gameService.registerGame(any())).thenReturn(stubResponse(42L));

        finalizer.scheduleAfterCommit(10L, req());

        verify(gameService, timeout(2000).times(1)).registerGame(any(RegisterGameRequest.class));
        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(broadcaster, timeout(2000)).publish(eq(10L), eq("game.finished"), cap.capture());
        assertThat(cap.getValue()).containsEntry("finalizedGameId", 42L);
        assertThat(s.getFinalizedGameId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("scheduleAfterCommit_alreadyMaterialized_skipsRegisterGame")
    void scheduleAfterCommit_alreadyMaterialized_skipsRegisterGame() {
        LiveGameSession s = LiveGameSession.builder().build();
        s.setId(10L);
        s.setFinalizedGameId(99L);
        when(sessionRepo.findById(eq(10L))).thenReturn(Optional.of(s));

        finalizer.scheduleAfterCommit(10L, req());

        verify(broadcaster, timeout(2000)).publish(eq(10L), eq("game.finished"), any());
        verify(gameService, never()).registerGame(any());
    }

    @Test
    @DisplayName("scheduleAfterCommit_transientFailure_retriesUpToThreeTimes")
    void scheduleAfterCommit_transientFailure_retriesUpToThreeTimes() throws Exception {
        LiveGameSession s = LiveGameSession.builder().build();
        s.setId(10L);
        when(sessionRepo.findById(eq(10L))).thenReturn(Optional.of(s));
        AtomicInteger calls = new AtomicInteger(0);
        when(gameService.registerGame(any())).thenAnswer(inv -> {
            calls.incrementAndGet();
            throw new RuntimeException("DB transient error");
        });

        finalizer.scheduleAfterCommit(10L, req());

        await().atMost(5, TimeUnit.SECONDS).until(() -> calls.get() >= 3);
        // Tras 3 fallos no debe broadcastear.
        verify(broadcaster, never()).publish(any(), any(), any());
    }

    @Test
    @DisplayName("shutdown_terminatesExecutor_doesNotThrow")
    void shutdown_terminatesExecutor_doesNotThrow() {
        finalizer.shutdown();
    }

    private GameResponse stubResponse(Long id) {
        return new GameResponse(id, 1L, 2L, "1-0", "CASUAL", 1500, 1500,
                1516, 1484, 10, null, null, null, null, 30, Instant.now(), Instant.now());
    }
}
