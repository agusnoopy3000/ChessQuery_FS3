package cl.chessquery.tournament.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Envelope genérico de los eventos del exchange ChessEvents.
 * El payload se deja como Map para soportar distintos tipos de evento.
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
