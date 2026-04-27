package cl.chessquery.users.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Tabla de idempotencia. Persiste el eventId de cada evento RabbitMQ
 * procesado para descartar duplicados (mismo patrón que en ms-notifications).
 */
@Entity
@Table(name = "processed_event")
@Getter @Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
public class ProcessedEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
