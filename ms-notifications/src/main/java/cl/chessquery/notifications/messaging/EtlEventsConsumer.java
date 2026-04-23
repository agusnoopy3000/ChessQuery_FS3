package cl.chessquery.notifications.messaging;

import cl.chessquery.notifications.config.RabbitMQConfig;
import cl.chessquery.notifications.entity.ProcessedEvent;
import cl.chessquery.notifications.repository.ProcessedEventRepository;
import cl.chessquery.notifications.service.NotificationService;
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
 * Procesa sync.completed con status FAILED para alertar al administrador.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EtlEventsConsumer {

    private final NotificationService      notificationService;
    private final ProcessedEventRepository processedEventRepo;

    @RabbitListener(queues = RabbitMQConfig.ETL_EVENTS_QUEUE)
    @Transactional
    public void onEtlEvent(ChessEvent event,
                           Channel channel,
                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (alreadyProcessed(event.getEventId())) {
            log.debug("Evento ETL ya procesado, ignorando: {}", event.getEventId());
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            if ("sync.completed".equals(event.getEventType())) {
                Map<String, Object> payload = event.getPayload();
                String status = (String) payload.get("status");
                if ("FAILED".equals(status)) {
                    notificationService.notifySyncFailed(payload);
                } else {
                    log.debug("sync.completed con status={}, no requiere alerta", status);
                }
            } else {
                log.debug("Tipo de evento ignorado en etl.events (notifications): {}", event.getEventType());
            }
            markProcessed(event.getEventId());
            channel.basicAck(deliveryTag, false);
        } catch (DataIntegrityViolationException e) {
            log.warn("Conflicto de idempotencia en etl.events para evento {}", event.getEventId());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Error procesando evento ETL {}: {}", event.getEventId(), e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
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
