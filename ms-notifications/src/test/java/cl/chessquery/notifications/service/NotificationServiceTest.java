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
import static org.mockito.ArgumentMatchers.isNull;
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
    void notifyWelcome_withSupabaseUuid_savesEmailLogWithNullRecipientAndSkipsInApp() {
        // Regression bug #3: en el flujo Supabase Auth el userId llega como
        // UUID antes de que el Player numérico exista. El email sigue siendo
        // entregable; el log queda con recipient_id=NULL y la fila in-app se
        // omite (no hay destinatario al que la campana le sirva).
        Map<String, Object> payload = Map.of(
                "userId",    "550e8400-e29b-41d4-a716-446655440000",
                "email",     "nuevo@email.com",
                "firstName", "Pedro",
                "lastName",  "Soto",
                "role",      "PLAYER"
        );
        when(notificationLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.notifyWelcome(payload);

        verify(mockEmailService).sendEmail(
                isNull(), eq("nuevo@email.com"),
                eq("¡Bienvenido a ChessQuery!"), anyString());

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepo, times(1)).save(captor.capture());
        NotificationLog saved = captor.getValue();
        assertThat(saved.getRecipientId()).isNull();
        assertThat(saved.getEventType()).isEqualTo("user.registered");
        assertThat(saved.getStatus()).isEqualTo(NotifStatus.SENT);
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
        verify(notificationLogRepo, times(1)).save(captor.capture()); // solo in-app, sin email
        NotificationLog saved = captor.getAllValues().get(0);
        assertThat(saved.getStatus()).isEqualTo(NotifStatus.SENT);
        assertThat(saved.getEventType()).isEqualTo("elo.updated");
        assertThat(saved.getRecipientId()).isEqualTo(12L);
        verify(mockEmailService, never()).sendEmail(any(), anyString(), anyString(), anyString());
    }

    @Test
    void notifyRegistration_persistsInAppLogNoEmail() {
        when(notificationLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        notificationService.notifyRegistration(Map.of(
                "playerId", 5, "tournamentId", 1,
                "tournamentName", "Magistral", "seedRating", 1500));
        verify(notificationLogRepo, times(1)).save(any()); // solo in-app
        verify(mockEmailService, never()).sendEmail(any(), anyString(), anyString(), anyString());
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
    void notifyRegistrationApproved_validPayload_savesInAppNoEmail() {
        when(notificationLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<NotificationLog> cap = ArgumentCaptor.forClass(NotificationLog.class);
        notificationService.notifyRegistrationApproved(Map.of(
                "playerId", 5, "tournamentName", "Magistral"));
        verify(notificationLogRepo, times(1)).save(cap.capture()); // solo in-app
        assertThat(cap.getValue().getSubject()).contains("Aprobado");
        verify(mockEmailService, never()).sendEmail(any(), anyString(), anyString(), anyString());
    }

    @Test
    void notifyRegistrationRejected_withReason_includesReasonInApp() {
        when(notificationLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        notificationService.notifyRegistrationRejected(Map.of(
                "playerId", 5, "tournamentName", "X", "reason", "ELO bajo"));
        ArgumentCaptor<NotificationLog> cap = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepo).save(cap.capture()); // solo in-app
        assertThat(cap.getValue().getSubject()).contains("ELO bajo");
        verify(mockEmailService, never()).sendEmail(any(), anyString(), anyString(), anyString());
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
    void notifyGameInvitation_unregisteredEmail_sendsEmailOnlyNoInApp() {
        // Invitado sin cuenta: playerId null + email presente. Debe enviar email
        // y NO crear in-app, sin NPE (regresión del toString sobre null).
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("gameId", 9);
        payload.put("playerId", null);
        payload.put("email", "invitado@example.com");
        payload.put("gameUrl", "http://portal/play/9");
        payload.put("inviterName", "Ana");
        notificationService.notifyGameInvitation(payload);
        verify(mockEmailService).sendEmail(isNull(), eq("invitado@example.com"), anyString(), anyString());
        verify(notificationLogRepo, never()).save(any());
    }


    @Test
    void notifyGameFinished_notifiesBothPlayers() {
        when(notificationLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(playerNameResolver.resolve(any())).thenReturn("Rival X");
        notificationService.notifyGameFinished(Map.of(
                "whitePlayerId", 1, "blackPlayerId", 2,
                "result", "1-0", "finalizedGameId", 99));
        // Solo in-app: 1 save por jugador × 2 jugadores = 2; sin email.
        verify(notificationLogRepo, times(2)).save(any());
        verify(mockEmailService, never()).sendEmail(any(), anyString(), anyString(), anyString());
    }

    @Test
    void notifyGameFinished_drawResult_describesAsTablas() {
        when(notificationLogRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(playerNameResolver.resolve(any())).thenReturn("R");
        notificationService.notifyGameFinished(Map.of(
                "whitePlayerId", 1, "blackPlayerId", 2,
                "result", "1/2-1/2", "finalizedGameId", 1));
        ArgumentCaptor<NotificationLog> cap = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepo, times(2)).save(cap.capture()); // in-app, sin email
        assertThat(cap.getAllValues().get(0).getSubject()).contains("Tablas");
        verify(mockEmailService, never()).sendEmail(any(), anyString(), anyString(), anyString());
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
    void notifyRoundStarting_soloLogSinEmail() {
        notificationService.notifyRoundStarting(Map.of(
                "tournamentId", 1, "roundNumber", 3, "pairingsCount", 7));
        // No envía email ni persiste log (los jugadores se enteran vía game.invitation).
        verify(mockEmailService, never()).sendEmail(any(), anyString(), anyString(), anyString());
        verify(notificationLogRepo, never()).save(any());
    }

    @Test
    void notifySyncFailed_soloLogSinEmail() {
        Map<String, Object> payload = Map.of(
                "source", "FIDE", "status", "FAILED",
                "recordsProcessed", 0, "recordsFailed", 0,
                "durationMs", 500, "circuitBreakerState", "OPEN"
        );
        notificationService.notifySyncFailed(payload);
        // Alerta ETL solo a log; sin email (los únicos correos son bienvenida e invitación).
        verify(mockEmailService, never()).sendEmail(any(), anyString(), anyString(), anyString());
        verify(notificationLogRepo, never()).save(any());
    }
}
