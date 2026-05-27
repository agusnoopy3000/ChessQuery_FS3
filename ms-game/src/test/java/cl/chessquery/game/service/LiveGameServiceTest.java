package cl.chessquery.game.service;

import cl.chessquery.game.dto.LiveGameDtos.*;
import cl.chessquery.game.dto.RegisterGameRequest;
import cl.chessquery.game.entity.LiveGameMove;
import cl.chessquery.game.entity.LiveGameSession;
import cl.chessquery.game.entity.LiveGameSession.SessionStatus;
import cl.chessquery.game.entity.Opening;
import cl.chessquery.game.exception.ApiException;
import cl.chessquery.game.opening.OpeningDetector;
import cl.chessquery.game.realtime.LiveGameBroadcaster;
import cl.chessquery.game.repository.LiveGameMoveRepository;
import cl.chessquery.game.repository.LiveGameSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de {@link LiveGameService}.
 *
 * <p>Mockea repositorios, broadcaster, openingDetector, eventos y RestTemplate.
 * No usa Spring context. Cada test parte de una sesión recién creada vía el
 * helper {@link #setUp()} que registra answers para simular la persistencia.</p>
 *
 * <p>Invariantes verificados:
 * <ul>
 *   <li>Las jugadas se persisten en el orden enviado y el PGN refleja ese orden.</li>
 *   <li>Una jugada ilegal nunca persiste y devuelve 400.</li>
 *   <li>Una jugada fuera de turno nunca persiste y devuelve 403.</li>
 *   <li>Resign/Timeout/Draw son idempotentes para reintentos del mismo jugador.</li>
 *   <li>El rematch invierte colores y crea sesión WAITING.</li>
 * </ul></p>
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
        live = new LiveGameService(sessionRepo, moveRepo, finalizer, broadcaster,
                openingDetector, events, restTemplate);

        when(sessionRepo.save(any(LiveGameSession.class))).thenAnswer(inv -> {
            LiveGameSession s = inv.getArgument(0);
            if (s.getId() == null) s.setId(sessionIdSeq.incrementAndGet());
            persistedSession = s;
            return s;
        });
        when(sessionRepo.findById(any())).thenAnswer(inv -> Optional.ofNullable(persistedSession));
        when(sessionRepo.findByIdForUpdate(any())).thenAnswer(inv -> Optional.ofNullable(persistedSession));

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
        when(moveRepo.countBySessionId(any())).thenAnswer(inv -> (long) persistedMoves.size());
    }

    private void createAndJoin() {
        live.create(new CreateLiveGameRequest(1L, 1500, 600_000L, 0L));
        live.join(1L, new JoinLiveGameRequest(2L, 1500));
    }

    private void play(String uci, Long playerId) {
        live.move(1L, new MoveRequest(playerId, uci, null, null));
    }

    @Nested
    @DisplayName("Flujo completo")
    class FlujoCompleto {

        @Test
        @DisplayName("fullFlow_create_join_fiveMoves_resign_producesValidPgn")
        void fullFlow_create_join_fiveMoves_resign_producesValidPgn() {
            LiveGameResponse created = live.create(new CreateLiveGameRequest(1L, 1500, 600_000L, 0L));
            assertThat(created.id()).isEqualTo(1L);
            assertThat(created.status()).isEqualTo("WAITING");

            live.join(1L, new JoinLiveGameRequest(2L, 1500));
            assertThat(persistedSession.getStatus()).isEqualTo(SessionStatus.ACTIVE);

            play("e2e4", 1L); play("e7e5", 2L);
            play("g1f3", 1L); play("b8c6", 2L);
            play("f1c4", 1L);
            assertThat(persistedMoves).hasSize(5);

            live.resign(1L, new ResignRequest(2L));

            ArgumentCaptor<RegisterGameRequest> captor = ArgumentCaptor.forClass(RegisterGameRequest.class);
            verify(finalizer).scheduleAfterCommit(eq(1L), captor.capture());
            String pgn = captor.getValue().pgnContent();

            assertThat(pgn)
                    .contains("[Event \"ChessQuery Live\"]")
                    .contains("[Site")
                    .contains("[Date")
                    .contains("[White \"Player 1\"]")
                    .contains("[Black \"Player 2\"]")
                    .contains("[Result \"1-0\"]")
                    .contains("[Termination \"Abandoned\"]")
                    .contains("[TimeControl \"600+0\"]");
            assertThat(pgn).contains("1. e4 e5").contains("2. Nf3 Nc6").contains("3. Bc4");
            assertThat(pgn.trim()).endsWith("1-0");

            assertThat(persistedSession.getStatus()).isEqualTo(SessionStatus.FINISHED);
            assertThat(persistedSession.getResult()).isEqualTo("1-0");
        }
    }

    @Nested
    @DisplayName("Validaciones de move")
    class Moves {

        @Test
        @DisplayName("move_illegalMove_returns400")
        void move_illegalMove_returns400() {
            createAndJoin();
            assertThatThrownBy(() -> live.move(1L, new MoveRequest(1L, "e2e5", null, null)))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 400);
        }

        @Test
        @DisplayName("move_invalidUci_returns400")
        void move_invalidUci_returns400() {
            createAndJoin();
            assertThatThrownBy(() -> live.move(1L, new MoveRequest(1L, "zzz", null, null)))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 400);
        }

        @Test
        @DisplayName("move_outOfTurn_returns403")
        void move_outOfTurn_returns403() {
            createAndJoin();
            assertThatThrownBy(() -> live.move(1L, new MoveRequest(2L, "e7e5", null, null)))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 403);
        }

        @Test
        @DisplayName("move_sessionNotActive_returns409")
        void move_sessionNotActive_returns409() {
            live.create(new CreateLiveGameRequest(1L, 1500, null, null));
            assertThatThrownBy(() -> live.move(1L, new MoveRequest(1L, "e2e4", null, null)))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 409);
        }

        @Test
        @DisplayName("move_sessionNotFound_returns404")
        void move_sessionNotFound_returns404() {
            assertThatThrownBy(() -> live.move(999L, new MoveRequest(1L, "e2e4", null, null)))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 404);
        }

        @Test
        @DisplayName("move_duplicateRetry_returnsSameStateWithoutCreatingAnotherMove")
        void move_duplicateRetry_returnsSameStateWithoutCreatingAnotherMove() {
            createAndJoin();
            live.move(1L, new MoveRequest(1L, "e2e4", null, null));
            LiveGameResponse retry = live.move(1L, new MoveRequest(1L, "e2e4", null, null));
            assertThat(retry.moves()).hasSize(1);
            assertThat(persistedMoves).hasSize(1);
            assertThat(retry.turn()).isEqualTo("b");
        }

        @Test
        @DisplayName("move_updatesClocksWhenProvided")
        void move_updatesClocksWhenProvided() {
            createAndJoin();
            live.move(1L, new MoveRequest(1L, "e2e4", 590_000L, 600_000L));
            assertThat(persistedSession.getClockWhiteMs()).isEqualTo(590_000L);
            assertThat(persistedSession.getClockBlackMs()).isEqualTo(600_000L);
        }
    }

    @Nested
    @DisplayName("Join")
    class Join {

        @Test
        @DisplayName("join_sameAsCreator_returns400")
        void join_sameAsCreator_returns400() {
            live.create(new CreateLiveGameRequest(1L, 1500, null, null));
            assertThatThrownBy(() -> live.join(1L, new JoinLiveGameRequest(1L, 1500)))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 400);
        }

        @Test
        @DisplayName("join_alreadyActiveByThirdParty_returns409")
        void join_alreadyActiveByThirdParty_returns409() {
            createAndJoin();
            assertThatThrownBy(() -> live.join(1L, new JoinLiveGameRequest(3L, 1500)))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 409);
        }

        @Test
        @DisplayName("join_blackRetryAfterJoin_returnsCurrentState")
        void join_blackRetryAfterJoin_returnsCurrentState() {
            createAndJoin();
            LiveGameResponse second = live.join(1L, new JoinLiveGameRequest(2L, 1500));
            assertThat(second.status()).isEqualTo("ACTIVE");
        }
    }

    @Nested
    @DisplayName("Resign / Timeout / Draw")
    class Endgame {

        @Test
        @DisplayName("resign_byWhite_resultIsBlackWins")
        void resign_byWhite_resultIsBlackWins() {
            createAndJoin();
            LiveGameResponse r = live.resign(1L, new ResignRequest(1L));
            assertThat(r.result()).isEqualTo("0-1");
            assertThat(persistedSession.getEndReason()).isEqualTo("RESIGN");
        }

        @Test
        @DisplayName("resign_byNonParticipant_returns403")
        void resign_byNonParticipant_returns403() {
            createAndJoin();
            assertThatThrownBy(() -> live.resign(1L, new ResignRequest(99L)))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 403);
        }

        @Test
        @DisplayName("resign_alreadyFinishedSameReason_returnsIdempotent")
        void resign_alreadyFinishedSameReason_returnsIdempotent() {
            createAndJoin();
            live.resign(1L, new ResignRequest(2L));
            LiveGameResponse retry = live.resign(1L, new ResignRequest(2L));
            assertThat(retry.result()).isEqualTo("1-0");
        }

        @Test
        @DisplayName("resign_sessionNotActive_throws409")
        void resign_sessionNotActive_throws409() {
            live.create(new CreateLiveGameRequest(1L, 1500, null, null));
            assertThatThrownBy(() -> live.resign(1L, new ResignRequest(1L)))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 409);
        }

        @Test
        @DisplayName("timeout_byWhite_blackWinsAndWhiteClockZero")
        void timeout_byWhite_blackWinsAndWhiteClockZero() {
            createAndJoin();
            LiveGameResponse r = live.timeout(1L, new ResignRequest(1L));
            assertThat(r.result()).isEqualTo("0-1");
            assertThat(persistedSession.getClockWhiteMs()).isZero();
            assertThat(persistedSession.getEndReason()).isEqualTo("TIMEOUT");
        }

        @Test
        @DisplayName("timeout_byNonParticipant_returns403")
        void timeout_byNonParticipant_returns403() {
            createAndJoin();
            assertThatThrownBy(() -> live.timeout(1L, new ResignRequest(99L)))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 403);
        }

        @Test
        @DisplayName("drawAgreement_result_isHalfHalf")
        void drawAgreement_result_isHalfHalf() {
            createAndJoin();
            LiveGameResponse r = live.drawAgreement(1L, new ResignRequest(1L));
            assertThat(r.result()).isEqualTo("1/2-1/2");
            assertThat(persistedSession.getEndReason()).isEqualTo("DRAW_AGREEMENT");
        }

        @Test
        @DisplayName("drawAgreement_byNonParticipant_returns403")
        void drawAgreement_byNonParticipant_returns403() {
            createAndJoin();
            assertThatThrownBy(() -> live.drawAgreement(1L, new ResignRequest(99L)))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 403);
        }
    }

    @Nested
    @DisplayName("Rematch")
    class Rematch {

        @Test
        @DisplayName("rematch_originalNotFinished_returns409")
        void rematch_originalNotFinished_returns409() {
            createAndJoin();
            assertThatThrownBy(() -> live.rematch(1L, new RematchRequest(1L)))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 409);
        }

        @Test
        @DisplayName("rematch_byNonParticipant_returns403")
        void rematch_byNonParticipant_returns403() {
            createAndJoin();
            live.resign(1L, new ResignRequest(1L));
            assertThatThrownBy(() -> live.rematch(1L, new RematchRequest(99L)))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 403);
        }

        @Test
        @DisplayName("rematch_swapsColors_blackBecomesWhite")
        void rematch_swapsColors_blackBecomesWhite() {
            createAndJoin();
            live.resign(1L, new ResignRequest(1L));
            // Simulamos que persistedSession se mantiene apuntando al rematch creado.
            // Tras rematch nuestra mock devuelve la nueva sesión vía persistedSession.
            LiveGameResponse rematch = live.rematch(1L, new RematchRequest(2L));
            assertThat(rematch.whitePlayerId()).isEqualTo(2L);
            assertThat(rematch.status()).isEqualTo("WAITING");
        }
    }

    @Nested
    @DisplayName("invitePlayer")
    class Invite {

        @Test
        @DisplayName("invitePlayer_blankEmail_returnsUnmatchedWithoutCallingMsUsers")
        void invitePlayer_blankEmail_returnsUnmatchedWithoutCallingMsUsers() {
            createAndJoin();
            Map<String, Object> r = live.invitePlayer(1L, "  ", null, 1L);
            assertThat(r).containsEntry("matched", false);
            verify(restTemplate, never()).getForObject(anyString(), eq(Map.class), any(Object[].class));
        }

        @Test
        @DisplayName("invitePlayer_msUsersNotFound_returnsUnmatched")
        void invitePlayer_msUsersNotFound_returnsUnmatched() {
            createAndJoin();
            when(restTemplate.getForObject(anyString(), eq(Map.class), anyString()))
                    .thenThrow(HttpClientErrorException.create(org.springframework.http.HttpStatus.NOT_FOUND,
                            "not found", null, null, null));
            Map<String, Object> r = live.invitePlayer(1L, "x@y.cl", null, 1L);
            assertThat(r).containsEntry("matched", false);
        }

        @Test
        @DisplayName("invitePlayer_lookupThrowsGeneric_returnsUnmatched")
        void invitePlayer_lookupThrowsGeneric_returnsUnmatched() {
            createAndJoin();
            when(restTemplate.getForObject(anyString(), eq(Map.class), anyString()))
                    .thenThrow(new RuntimeException("net down"));
            Map<String, Object> r = live.invitePlayer(1L, "x@y.cl", null, 1L);
            assertThat(r).containsEntry("matched", false);
        }

        @Test
        @DisplayName("invitePlayer_matchesInviterId_doesNotPublish")
        void invitePlayer_matchesInviterId_doesNotPublish() {
            createAndJoin();
            Map<String, Object> body = new HashMap<>();
            body.put("id", 1);
            body.put("firstName", "A"); body.put("lastName", "B");
            when(restTemplate.getForObject(anyString(), eq(Map.class), anyString())).thenReturn(body);
            Map<String, Object> r = live.invitePlayer(1L, "x@y.cl", null, 1L);
            assertThat(r).containsEntry("matched", false);
            verify(events, never()).publishGameInvitation(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("invitePlayer_validMatch_publishesInvitation")
        void invitePlayer_validMatch_publishesInvitation() {
            createAndJoin();
            Map<String, Object> body = new HashMap<>();
            body.put("id", 5);
            body.put("firstName", "Alice"); body.put("lastName", "Doe");
            when(restTemplate.getForObject(anyString(), eq(Map.class), anyString())).thenReturn(body);

            Map<String, Object> r = live.invitePlayer(1L, "x@y.cl", "https://url", 1L);

            assertThat(r).containsEntry("matched", true).containsEntry("playerId", 5L);
            verify(events).publishGameInvitation(eq(1L), eq(5L), eq(1L),
                    eq("el creador de la partida"), eq("https://url"));
        }
    }

    @Nested
    @DisplayName("get + opening detection")
    class GetWithOpening {

        @Test
        @DisplayName("get_returnsCurrentSessionState")
        void get_returnsCurrentSessionState() {
            createAndJoin();
            play("e2e4", 1L);
            play("e7e5", 2L);
            when(openingDetector.detectOpening(anyString()))
                    .thenReturn(Optional.of(Opening.builder().id(1).ecoCode("C20").name("King's Pawn").build()));
            LiveGameResponse r = live.get(1L);
            assertThat(r.detectedOpeningEco()).isEqualTo("C20");
            assertThat(r.detectedOpeningName()).isEqualTo("King's Pawn");
        }

        @Test
        @DisplayName("get_openingDetectorThrows_doesNotFailResponse")
        void get_openingDetectorThrows_doesNotFailResponse() {
            createAndJoin();
            play("e2e4", 1L); play("e7e5", 2L);
            when(openingDetector.detectOpening(anyString())).thenThrow(new RuntimeException("boom"));
            LiveGameResponse r = live.get(1L);
            assertThat(r.detectedOpeningEco()).isNull();
        }
    }
}
