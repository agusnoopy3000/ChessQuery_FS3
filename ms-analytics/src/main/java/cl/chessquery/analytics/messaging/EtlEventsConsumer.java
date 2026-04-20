package cl.chessquery.analytics.messaging;

import cl.chessquery.analytics.config.RabbitMQConfig;
import cl.chessquery.analytics.entity.ProcessedEvent;
import cl.chessquery.analytics.repository.ProcessedEventRepository;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Consumidor de la cola etl.events del exchange ChessEvents.
 * Procesa el evento rating.updated que llega cuando el ETL termina una sincronización masiva.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EtlEventsConsumer {

    private final ProcessedEventRepository processedEventRepo;

    @RabbitListener(queues = RabbitMQConfig.ETL_EVENTS_QUEUE)
    @Transactional
    public void onEtlEvent(ChessEvent event,
                           Channel channel,
                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        // Idempotencia
        if (alreadyProcessed(event.getEventId())) {
            log.debug("Evento ETL ya procesado, ignorando: {}", event.getEventId());
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            if ("rating.updated".equals(event.getEventType())) {
                processRatingUpdated(event.getPayload());
            } else {
                log.debug("Tipo de evento ignorado en etl.events: {}", event.getEventType());
            }
            markProcessed(event.getEventId());
            channel.basicAck(deliveryTag, false);
        } catch (DataIntegrityViolationException e) {
            log.warn("Conflicto de idempotencia para evento ETL {}, haciendo ACK", event.getEventId());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Error procesando evento ETL {}: {}", event.getEventId(), e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private void processRatingUpdated(Map<String, Object> payload) {
        // rating.updated llega cuando el ETL sincroniza ratings en masa.
        // No tenemos los IDs individuales de jugadores en este evento,
        // solo loguear la actualización masiva para trazabilidad.
        String source         = (String) payload.get("source");
        Object playersUpdated = payload.get("playersUpdated");
        Object ratingType     = payload.get("ratingType");
        Object syncId         = payload.get("syncId");

        log.info("rating.updated recibido: source={} playersUpdated={} ratingType={} syncId={}",
                source, playersUpdated, ratingType, syncId);
        // Cuando MS-Analytics sea completamente implementado, aquí se podría
        // disparar una re-agregación de estadísticas afectadas.
    }

    private boolean alreadyProcessed(String eventId) {
        return eventId != null && processedEventRepo.existsById(eventId);
    }

    private void markProcessed(String eventId) {
        if (eventId != null) {
            processedEventRepo.save(ProcessedEvent.builder()
                    .eventId(eventId)
                    .processedAt(Instant.now())
                    .build());
        }
    }
}
