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

        // Assert — se guarda el log con status SENT
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepo, times(1)).save(captor.capture());
        NotificationLog saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(NotifStatus.SENT);
        assertThat(saved.getEventType()).isEqualTo("user.registered");
        assertThat(saved.getRecipientId()).isEqualTo(12L);
        assertThat(saved.getSentAt()).isNotNull();
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
        verify(notificationLogRepo, times(1)).save(captor.capture());
        NotificationLog saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(NotifStatus.SENT);
        assertThat(saved.getEventType()).isEqualTo("elo.updated");
        assertThat(saved.getRecipientId()).isEqualTo(12L);
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
