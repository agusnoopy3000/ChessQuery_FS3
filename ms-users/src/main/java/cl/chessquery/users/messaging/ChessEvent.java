package cl.chessquery.users.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Envelope genérico para todos los eventos del exchange ChessEvents.
 * El payload se deserializa como Map para soportar distintos event types.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChessEvent {
    private String eventId;
    private String eventType;
    private Instant timestamp;
    private Map<String, Object> payload;
}
