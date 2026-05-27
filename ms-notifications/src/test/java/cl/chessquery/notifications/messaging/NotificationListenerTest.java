package cl.chessquery.notifications.messaging;

import cl.chessquery.notifications.repository.ProcessedEventRepository;
import cl.chessquery.notifications.service.NotificationService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    @Mock private NotificationService notificationService;
    @Mock private ProcessedEventRepository processedEventRepo;
    @Mock private Channel channel;

    @InjectMocks
    private TournamentEventsConsumer consumer;

    private ChessEvent buildEvent(String type, String id, Map<String, Object> payload) {
        ChessEvent ev = new ChessEvent();
        ev.setEventId(id);
        ev.setEventType(type);
        ev.setTimestamp(java.time.Instant.now());
        ev.setPayload(payload);
        return ev;
    }

    @Test
    void onTournamentEvent_playerRegistered_invokesNotifyRegistration() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("playerId", 55L);
        payload.put("tournamentId", 1L);
        ChessEvent event = buildEvent("player.registered", "evt-1", payload);

        when(processedEventRepo.existsById("evt-1")).thenReturn(false);

        consumer.onTournamentEvent(event, channel, 42L);

        verify(notificationService, times(1)).notifyRegistration(eq(payload));
        verify(channel, times(1)).basicAck(eq(42L), anyBoolean());
    }

    @Test
    void onTournamentEvent_alreadyProcessed_skipsServiceAndAcks() throws Exception {
        ChessEvent event = buildEvent("player.registered", "evt-dup", new HashMap<>());
        when(processedEventRepo.existsById("evt-dup")).thenReturn(true);

        consumer.onTournamentEvent(event, channel, 7L);

        verifyNoInteractions(notificationService);
        verify(channel, times(1)).basicAck(eq(7L), anyBoolean());
    }

    @Test
    void onTournamentEvent_serviceThrows_nacksWithoutRequeue() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        ChessEvent event = buildEvent("player.registered", "evt-err", payload);
        when(processedEventRepo.existsById("evt-err")).thenReturn(false);
        doThrow(new RuntimeException("smtp down"))
                .when(notificationService).notifyRegistration(any());

        consumer.onTournamentEvent(event, channel, 99L);

        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(channel, times(1)).basicNack(eq(99L), eq(false), eq(false));
    }
}
