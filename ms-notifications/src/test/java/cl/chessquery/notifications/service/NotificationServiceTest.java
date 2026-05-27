package cl.chessquery.notifications.service;

import cl.chessquery.notifications.entity.NotifStatus;
import cl.chessquery.notifications.entity.NotificationLog;
import cl.chessquery.notifications.repository.NotificationLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private MockEmailService          mockEmailService;
    @Mock
    private NotificationLogRepository notificationLogRepo;
    @Spy
    private ObjectMapper              objectMapper = new ObjectMapper();
    @Mock
    private PlayerNameResolver        playerNameResolver;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void notifyWelcome_createsNotificationLogWithStatusSent() {
        // Arrange
        Map<String, Object> payload = Map.of(
                "userId",    12,
                "email",     "cristobal@email.com",
                "firstName", "Cristóbal",
                "lastName",  "Henríquez",
                "role",      "PLAYER"
        );
        when(notificationLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        notificationService.notifyWelcome(payload);

        // Assert — se envía el email mock
        verify(mockEmailService, times(1)).sendEmail(
                eq(12L), eq("cristobal@email.com"),
                eq("¡Bienvenido a ChessQuery!"), anyString());

        // Assert — se guardan los logs con status SENT (EMAIL y IN_APP)
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepo, times(2)).save(captor.capture());
        NotificationLog savedEmail = captor.getAllValues().get(0);
        assertThat(savedEmail.getStatus()).isEqualTo(NotifStatus.SENT);
        assertThat(savedEmail.getEventType()).isEqualTo("user.registered");
        assertThat(savedEmail.getRecipientId()).isEqualTo(12L);
        assertThat(savedEmail.getSentAt()).isNotNull();
    }

    @Test
    void notifyEloUpdated_createsNotificationLogWithStatusSent() {
        // Arrange
        Map<String, Object> payload = Map.of(
                "playerId",   12,
                "oldElo",     1750,
                "newElo",     1762,
                "delta",      12,
                "ratingType", "FIDE_STANDARD",
                "gameId",     4521
        );
        when(notificationLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        notificationService.notifyEloUpdated(payload);

        // Assert
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepo, times(2)).save(captor.capture());
        NotificationLog saved = captor.getAllValues().get(0);
        assertThat(saved.getStatus()).isEqualTo(NotifStatus.SENT);
        assertThat(saved.getEventType()).isEqualTo("elo.updated");
        assertThat(saved.getRecipientId()).isEqualTo(12L);
    }

    @Test
    void notifyRegistration_persistsEmailAndInAppLog() {
        when(notificationLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        notificationService.notifyRegistration(Map.of(
                "playerId", 5, "tournamentId", 1,
                "tournamentName", "Magistral", "seedRating", 1500));
        verify(notificationLogRepo, times(2)).save(any());
        verify(mockEmailService).sendEmail(eq(5L), anyString(), anyString(), anyString());
    }


    @Test
    void notifyRegistrationPending_validPayload_savesInAppToOrganizer() {
        when(notificationLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(playerNameResolver.resolve(eq(5L))).thenReturn("Ana Soto");
        notificationService.notifyRegistrationPending(Map.of(
                "tournamentId", 1, "playerId", 5,
                "organizerId", 9, "tournamentName", "Open"));
        ArgumentCaptor<NotificationLog> cap = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepo).save(cap.capture());
        assertThat(cap.getValue().getRecipientId()).isEqualTo(9L);
        assertThat(cap.getValue().getSubject()).contains("Ana Soto");
    }


    @Test
    void notifyRegistrationApproved_validPayload_sendsEmailAndInApp() {
        when(notificationLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        notificationService.notifyRegistrationApproved(Map.of(
                "playerId", 5, "tournamentName", "Magistral"));
        verify(notificationLogRepo, times(2)).save(any());
        verify(mockEmailService).sendEmail(eq(5L), anyString(),
                contains("aprobada"), anyString());
    }

    @Test
    void notifyRegistrationRejected_withReason_includesReasonInBody() {
        when(notificationLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        notificationService.notifyRegistrationRejected(Map.of(
                "playerId", 5, "tournamentName", "X", "reason", "ELO bajo"));
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mockEmailService).sendEmail(eq(5L), anyString(), anyString(), body.capture());
        assertThat(body.getValue()).contains("ELO bajo");
    }

    @Test
    void notifyGameInvitation_validPayload_savesInAppLog() {
        when(notificationLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        notificationService.notifyGameInvitation(Map.of(
                "gameId", 42, "playerId", 7, "inviterName", "Magnus"));
        ArgumentCaptor<NotificationLog> cap = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepo).save(cap.capture());
        assertThat(cap.getValue().getRecipientId()).isEqualTo(7L);
        assertThat(cap.getValue().getSubject()).contains("Magnus").contains("42");
    }


    @Test
    void notifyGameFinished_notifiesBothPlayers() {
        when(notificationLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(playerNameResolver.resolve(any())).thenReturn("Rival X");
        notificationService.notifyGameFinished(Map.of(
                "whitePlayerId", 1, "blackPlayerId", 2,
                "result", "1-0", "finalizedGameId", 99));
        // 2 saves por jugador × 2 jugadores = 4
        verify(notificationLogRepo, times(4)).save(any());
        verify(mockEmailService, times(2)).sendEmail(any(), anyString(), anyString(), anyString());
    }

    @Test
    void notifyGameFinished_drawResult_describesAsTablas() {
        when(notificationLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(playerNameResolver.resolve(any())).thenReturn("R");
        notificationService.notifyGameFinished(Map.of(
                "whitePlayerId", 1, "blackPlayerId", 2,
                "result", "1/2-1/2", "finalizedGameId", 1));
        ArgumentCaptor<String> subj = ArgumentCaptor.forClass(String.class);
        verify(mockEmailService, times(2)).sendEmail(any(), anyString(), subj.capture(), anyString());
        assertThat(subj.getAllValues().get(0)).contains("Tablas");
    }

    @Test
    void notifyTournamentCreated_validPayload_savesInAppForOrganizer() {
        when(notificationLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        notificationService.notifyTournamentCreated(Map.of(
                "organizerId", 9, "tournamentId", 1, "name", "Open"));
        ArgumentCaptor<NotificationLog> cap = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepo).save(cap.capture());
        assertThat(cap.getValue().getRecipientId()).isEqualTo(9L);
        assertThat(cap.getValue().getSubject()).contains("Open");
    }


    @Test
    void notifyRoundStarting_persistsLogAndEmail() {
        when(notificationLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        notificationService.notifyRoundStarting(Map.of(
                "tournamentId", 1, "roundNumber", 3, "pairingsCount", 7));
        verify(notificationLogRepo, times(1)).save(any());
        verify(mockEmailService).sendEmail(eq(1L), anyString(),
                contains("ronda"), anyString());
    }

    @Test
    void notifySyncFailed_alertsAdminWithRecipientIdZero() {
        // Arrange
        Map<String, Object> payload = Map.of(
                "source",               "FIDE",
                "status",               "FAILED",
                "recordsProcessed",     0,
                "recordsFailed",        0,
                "durationMs",           500,
                "circuitBreakerState",  "OPEN"
        );
        when(notificationLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        notificationService.notifySyncFailed(payload);

        // Assert — recipientId = 0 (admin)
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepo, times(1)).save(captor.capture());
        NotificationLog saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(NotifStatus.SENT);
        assertThat(saved.getRecipientId()).isEqualTo(0L);
        assertThat(saved.getSubject()).contains("ALERTA");
    }
}
