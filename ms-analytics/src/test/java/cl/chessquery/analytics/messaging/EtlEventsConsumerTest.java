package cl.chessquery.analytics.messaging;

import cl.chessquery.analytics.entity.ProcessedEvent;
import cl.chessquery.analytics.repository.ProcessedEventRepository;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link EtlEventsConsumer}: idempotencia, ruteo por tipo de
 * evento y semántica de ack/nack ante conflictos y errores.
 */
@ExtendWith(MockitoExtension.class)
class EtlEventsConsumerTest {

    @Mock private ProcessedEventRepository processedEventRepo;
    @Mock private Channel                  channel;

    @InjectMocks private EtlEventsConsumer consumer;

    @BeforeEach
    void setUp() {
        lenient().when(processedEventRepo.existsById(any())).thenReturn(false);
        lenient().when(processedEventRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ChessEvent event(String id, String type, Map<String, Object> payload) {
        ChessEvent e = new ChessEvent();
        e.setEventId(id);
        e.setEventType(type);
        e.setPayload(payload);
        return e;
    }

    @Test
    void onEtlEvent_ratingUpdated_marksProcessedAndAcks() throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("source", "lichess");
        payload.put("playersUpdated", 120);
        payload.put("ratingType", "BLITZ");
        payload.put("syncId", "sync-1");

        consumer.onEtlEvent(event("etl-1", "rating.updated", payload), channel, 1L);

        verify(processedEventRepo).save(any(ProcessedEvent.class));
        verify(channel).basicAck(1L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void onEtlEvent_duplicate_isIgnored() throws IOException {
        when(processedEventRepo.existsById("etl-dup")).thenReturn(true);

        consumer.onEtlEvent(event("etl-dup", "rating.updated", Map.of("source", "x")), channel, 2L);

        verify(processedEventRepo, never()).save(any());
        verify(channel).basicAck(2L, false);
    }

    @Test
    void onEtlEvent_unknownType_acksAndMarksWithoutProcessing() throws IOException {
        consumer.onEtlEvent(event("etl-other", "game.finished", Map.of()), channel, 3L);

        // El tipo se ignora, pero igual se marca como procesado (idempotencia) y se ACKea.
        verify(processedEventRepo).save(any(ProcessedEvent.class));
        verify(channel).basicAck(3L, false);
    }

    @Test
    void onEtlEvent_nullEventId_skipsIdempotencyAndAcks() throws IOException {
        // eventId null → no se consulta ni guarda el ProcessedEvent, pero el flujo ACKea igual.
        consumer.onEtlEvent(event(null, "rating.updated", Map.of("source", "x")), channel, 4L);

        verify(processedEventRepo, never()).existsById(any());
        verify(processedEventRepo, never()).save(any());
        verify(channel).basicAck(4L, false);
    }

    @Test
    void onEtlEvent_dataIntegrityViolation_acksInsteadOfNack() throws IOException {
        doThrow(new DataIntegrityViolationException("dup")).when(processedEventRepo).save(any());

        consumer.onEtlEvent(event("etl-race", "rating.updated", Map.of("source", "x")), channel, 5L);

        verify(channel).basicAck(5L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void onEtlEvent_unexpectedException_nacksWithoutRequeue() throws IOException {
        doThrow(new RuntimeException("DB caída")).when(processedEventRepo).save(any());

        consumer.onEtlEvent(event("etl-fail", "rating.updated", Map.of("source", "x")), channel, 6L);

        verify(channel).basicNack(6L, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }
}
