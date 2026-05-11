package cl.chessquery.game.service;

import cl.chessquery.game.dto.LiveGameDtos.*;
import cl.chessquery.game.dto.RegisterGameRequest;
import cl.chessquery.game.entity.LiveGameMove;
import cl.chessquery.game.entity.LiveGameSession;
import cl.chessquery.game.entity.LiveGameSession.SessionStatus;
import cl.chessquery.game.exception.ApiException;
import cl.chessquery.game.opening.OpeningDetector;
import cl.chessquery.game.realtime.LiveGameBroadcaster;
import cl.chessquery.game.repository.LiveGameMoveRepository;
import cl.chessquery.game.repository.LiveGameSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * R17 — cubre el flujo end-to-end de LiveGameService con repositorios y
 * dependencias externas mockeadas (sin Spring context).
 *
 * Casos:
 *  - create + join + 5 moves legales + resign → finishSession con PGN bien formado.
 *  - move ilegal → ApiException 400.
 *  - move fuera de turno → ApiException 403.
 *  - PGN respeta el orden de jugadas (regression del fix f176e0c).
 */
class LiveGameServiceTest {

    private LiveGameSessionRepository sessionRepo;
    private LiveGameMoveRepository moveRepo;
    private LiveGameFinalizer finalizer;
    private LiveGameBroadcaster broadcaster;
    private OpeningDetector openingDetector;
    private EventPublisherService events;
    private RestTemplate restTemplate;
    private LiveGameService live;

    private final AtomicLong sessionIdSeq = new AtomicLong(0);
    private final AtomicLong moveIdSeq = new AtomicLong(0);
    private final List<LiveGameMove> persistedMoves = new ArrayList<>();
    private LiveGameSession persistedSession;

    @BeforeEach
    void setUp() {
        sessionRepo = mock(LiveGameSessionRepository.class);
        moveRepo = mock(LiveGameMoveRepository.class);
        finalizer = mock(LiveGameFinalizer.class);
        broadcaster = mock(LiveGameBroadcaster.class);
        openingDetector = mock(OpeningDetector.class);
        events = mock(EventPublisherService.class);
        restTemplate = mock(RestTemplate.class);
        live = new LiveGameService(sessionRepo, moveRepo, finalizer, broadcaster, openingDetector, events, restTemplate);

        // sessionRepo.save() → asigna id incremental y guarda última versión.
        when(sessionRepo.save(any(LiveGameSession.class))).thenAnswer(inv -> {
            LiveGameSession s = inv.getArgument(0);
            if (s.getId() == null) s.setId(sessionIdSeq.incrementAndGet());
            persistedSession = s;
            return s;
        });
        when(sessionRepo.findById(any())).thenAnswer(inv -> Optional.ofNullable(persistedSession));

        when(moveRepo.save(any(LiveGameMove.class))).thenAnswer(inv -> {
            LiveGameMove m = inv.getArgument(0);
            if (m.getId() == null) m.setId(moveIdSeq.incrementAndGet());
            if (m.getCreatedAt() == null) m.setCreatedAt(Instant.now());
            persistedMoves.add(m);
            return m;
        });
        when(moveRepo.findBySessionIdOrderByCreatedAtAsc(any()))
                .thenAnswer(inv -> new ArrayList<>(persistedMoves));
        when(moveRepo.findTopBySessionIdOrderByCreatedAtDesc(any()))
                .thenAnswer(inv -> persistedMoves.isEmpty()
                        ? Optional.empty()
                        : Optional.of(persistedMoves.get(persistedMoves.size() - 1)));
        when(moveRepo.countBySessionId(any()))
                .thenAnswer(inv -> (long) persistedMoves.size());
    }

    @Test
    void fullFlow_create_join_fiveMoves_resign_producesValidPgn() {
        // 1. Crear sesión (white = 1)
        LiveGameResponse created = live.create(new CreateLiveGameRequest(
                1L, 1500, 600_000L, 0L));
        assertThat(created.id()).isEqualTo(1L);
        assertThat(created.status()).isEqualTo("WAITING");

        // 2. Join (black = 2)
        live.join(1L, new JoinLiveGameRequest(2L, 1500));
        assertThat(persistedSession.getStatus()).isEqualTo(SessionStatus.ACTIVE);

        // 3. 5 jugadas legales alternando turnos.
        play(1L, "e2e4", 1L);
        play(1L, "e7e5", 2L);
        play(1L, "g1f3", 1L);
        play(1L, "b8c6", 2L);
        play(1L, "f1c4", 1L);
        assertThat(persistedMoves).hasSize(5);

        // 4. Resign — black (jugador 2) abandona → resultado "1-0".
        live.resign(1L, new ResignRequest(2L));

        ArgumentCaptor<RegisterGameRequest> captor = ArgumentCaptor.forClass(RegisterGameRequest.class);
        verify(finalizer).scheduleAfterCommit(eq(1L), captor.capture());
        String pgn = captor.getValue().pgnContent();

        // PGN bien formado: Seven Tag Roster + jugadas en SAN + resultado.
        assertThat(pgn)
                .contains("[Event \"ChessQuery Live\"]")
                .contains("[Site")
                .contains("[Date")
                .contains("[White \"Player 1\"]")
                .contains("[Black \"Player 2\"]")
                .contains("[Result \"1-0\"]");
        // Orden de jugadas (regression test del fix f176e0c).
        assertThat(pgn).contains("1. e4 e5");
        assertThat(pgn).contains("2. Nf3 Nc6");
        assertThat(pgn).contains("3. Bc4");
        // El resultado aparece al final del movetext.
        assertThat(pgn.trim()).endsWith("1-0");

        assertThat(persistedSession.getStatus()).isEqualTo(SessionStatus.FINISHED);
        assertThat(persistedSession.getResult()).isEqualTo("1-0");
    }

    @Test
    void illegalMove_returns400() {
        live.create(new CreateLiveGameRequest(1L, 1500, null, null));
        live.join(1L, new JoinLiveGameRequest(2L, 1500));

        // e2e5 es ilegal en la posición inicial (peón no salta dos veces así).
        assertThatThrownBy(() -> live.move(1L, new MoveRequest(1L, "e2e5", null, null)))
                .isInstanceOf(ApiException.class)
                .matches(e -> ((ApiException) e).getStatus() == 400);
    }

    @Test
    void moveOutOfTurn_returns403() {
        live.create(new CreateLiveGameRequest(1L, 1500, null, null));
        live.join(1L, new JoinLiveGameRequest(2L, 1500));

        // Es turno de blancas (1), pero negras (2) intenta mover.
        assertThatThrownBy(() -> live.move(1L, new MoveRequest(2L, "e7e5", null, null)))
                .isInstanceOf(ApiException.class)
                .matches(e -> ((ApiException) e).getStatus() == 403);
    }

    @Test
    void duplicateMoveRetry_returnsCurrentStateWithoutCreatingAnotherMove() {
        live.create(new CreateLiveGameRequest(1L, 1500, null, null));
        live.join(1L, new JoinLiveGameRequest(2L, 1500));

        live.move(1L, new MoveRequest(1L, "e2e4", null, null));
        LiveGameResponse retry = live.move(1L, new MoveRequest(1L, "e2e4", null, null));

        assertThat(retry.moves()).hasSize(1);
        assertThat(persistedMoves).hasSize(1);
        assertThat(retry.turn()).isEqualTo("b");
    }

    private void play(Long sessionId, String uci, Long playerId) {
        live.move(sessionId, new MoveRequest(playerId, uci, null, null));
    }
}
