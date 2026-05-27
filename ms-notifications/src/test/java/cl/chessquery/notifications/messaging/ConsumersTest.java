package cl.chessquery.notifications.messaging;

import cl.chessquery.notifications.repository.ProcessedEventRepository;
import cl.chessquery.notifications.service.NotificationService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.dao.DataIntegrityViolationException;

/**
 * Tests unitarios de los 4 consumers de RabbitMQ:
 * {@link UserEventsConsumer}, {@link EtlEventsConsumer},
 * {@link GameEventsConsumer}, {@link TournamentEventsConsumer}.
 *
 * <p>Verifica idempotencia, ack/nack, routing por eventType y
 * tolerancia a fallos del service.</p>
 */
class ConsumersTest {

    NotificationService notif;
    ProcessedEventRepository processedRepo;
    Channel ch;

    UserEventsConsumer user;
    GameEventsConsumer game;
    EtlEventsConsumer etl;
    TournamentEventsConsumer tour;

    @BeforeEach
    void setUp() {
        notif = mock(NotificationService.class);
        processedRepo = mock(ProcessedEventRepository.class);
        ch = mock(Channel.class);
        user = new UserEventsConsumer(notif, processedRepo);
        game = new GameEventsConsumer(notif, processedRepo);
        etl = new EtlEventsConsumer(notif, processedRepo);
        tour = new TournamentEventsConsumer(notif, processedRepo);
        lenient().when(processedRepo.existsById(any())).thenReturn(false);
    }

    private ChessEvent event(String type, Map<String, Object> payload) {
        ChessEvent e = new ChessEvent();
        e.setEventId("evt-" + Math.random());
        e.setEventType(type);
        e.setTimestamp(Instant.now());
        e.setPayload(payload);
        return e;
    }

    // ── UserEventsConsumer ────────────────────────────────────────────────

    @Test
    @DisplayName("onUserEvent_alreadyProcessed_acksWithoutDispatch")
    void onUserEvent_alreadyProcessed_acksWithoutDispatch() throws IOException {
        when(processedRepo.existsById(any())).thenReturn(true);
        user.onUserEvent(event("user.registered", Map.of("userId", 1)), ch, 1L);
        verify(notif, never()).notifyWelcome(any());
        verify(ch).basicAck(eq(1L), eq(false));
    }

    @Test
    @DisplayName("onUserEvent_userRegistered_invokesNotifyWelcome")
    void onUserEvent_userRegistered_invokesNotifyWelcome() throws IOException {
        user.onUserEvent(event("user.registered", Map.of("userId", 1)), ch, 1L);
        verify(notif).notifyWelcome(any());
        verify(ch).basicAck(eq(1L), eq(false));
    }

    @Test
    @DisplayName("onUserEvent_unknownType_acksWithoutDispatch")
    void onUserEvent_unknownType_acksWithoutDispatch() throws IOException {
        user.onUserEvent(event("user.other", Map.of()), ch, 2L);
        verify(notif, never()).notifyWelcome(any());
        verify(ch).basicAck(eq(2L), eq(false));
    }

    @Test
    @DisplayName("onUserEvent_serviceThrows_nacksWithoutRequeue")
    void onUserEvent_serviceThrows_nacksWithoutRequeue() throws IOException {
        doThrow(new RuntimeException("boom")).when(notif).notifyWelcome(any());
        user.onUserEvent(event("user.registered", Map.of()), ch, 3L);
        verify(ch).basicNack(eq(3L), eq(false), eq(false));
    }

    @Test
    @DisplayName("onUserEvent_dataIntegrityViolation_acksAndContinues")
    void onUserEvent_dataIntegrityViolation_acksAndContinues() throws IOException {
        doThrow(new DataIntegrityViolationException("dup"))
                .when(notif).notifyWelcome(any());
        user.onUserEvent(event("user.registered", Map.of()), ch, 4L);
        verify(ch).basicAck(eq(4L), eq(false));
    }

    // ── GameEventsConsumer ────────────────────────────────────────────────

    @Test
    @DisplayName("onGameEvent_eloUpdated_routesToNotifyEloUpdated")
    void onGameEvent_eloUpdated_routesToNotifyEloUpdated() throws IOException {
        game.onGameEvent(event("elo.updated", Map.of("playerId", 1)), ch, 1L);
        verify(notif).notifyEloUpdated(any());
    }

    @Test
    @DisplayName("onGameEvent_gameFinished_routesToNotifyGameFinished")
    void onGameEvent_gameFinished_routesToNotifyGameFinished() throws IOException {
        game.onGameEvent(event("game.finished", Map.of()), ch, 1L);
        verify(notif).notifyGameFinished(any());
    }

    @Test
    @DisplayName("onGameEvent_gameInvitation_routesToNotifyGameInvitation")
    void onGameEvent_gameInvitation_routesToNotifyGameInvitation() throws IOException {
        game.onGameEvent(event("game.invitation", Map.of()), ch, 1L);
        verify(notif).notifyGameInvitation(any());
    }

    @Test
    @DisplayName("onGameEvent_unknownType_ignoredAndAcked")
    void onGameEvent_unknownType_ignoredAndAcked() throws IOException {
        game.onGameEvent(event("other", Map.of()), ch, 1L);
        verify(ch).basicAck(eq(1L), eq(false));
    }

    // ── EtlEventsConsumer ─────────────────────────────────────────────────

    @Test
    @DisplayName("onEtlEvent_syncFailed_notifiesAdmin")
    void onEtlEvent_syncFailed_notifiesAdmin() throws IOException {
        etl.onEtlEvent(event("sync.completed",
                Map.of("status", "FAILED", "source", "FIDE")), ch, 1L);
        verify(notif).notifySyncFailed(any());
    }

    @Test
    @DisplayName("onEtlEvent_syncOk_doesNotNotify")
    void onEtlEvent_syncOk_doesNotNotify() throws IOException {
        etl.onEtlEvent(event("sync.completed", Map.of("status", "OK")), ch, 1L);
        verify(notif, never()).notifySyncFailed(any());
    }

    @Test
    @DisplayName("onEtlEvent_unknownType_ignored")
    void onEtlEvent_unknownType_ignored() throws IOException {
        etl.onEtlEvent(event("other", Map.of()), ch, 1L);
        verify(notif, never()).notifySyncFailed(any());
    }

    // ── TournamentEventsConsumer ──────────────────────────────────────────

    @Test
    @DisplayName("onTournamentEvent_alreadyProcessed_acksWithoutDispatch")
    void onTournamentEvent_alreadyProcessed_acksWithoutDispatch() throws IOException {
        when(processedRepo.existsById(any())).thenReturn(true);
        tour.onTournamentEvent(event("player.registered", Map.of()), ch, 1L);
        verify(ch).basicAck(eq(1L), eq(false));
    }
}
