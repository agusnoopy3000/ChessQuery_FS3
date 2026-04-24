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

/**
 * Consumidor de la cola notifications.game.events (binding elo.*).
 *
 * NOTA: se usa una cola dedicada "notifications.game.events" en lugar de "game.events"
 * para evitar el patrón competing consumers con MS-Analytics.
 * Cada microservicio consumidor debe tener su propia cola.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameEventsConsumer {

    private final NotificationService      notificationService;
    private final ProcessedEventRepository processedEventRepo;

    @RabbitListener(queues = RabbitMQConfig.NOTIF_GAME_EVENTS_QUEUE)
    @Transactional
    public void onGameEvent(ChessEvent event,
                            Channel channel,
                            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (alreadyProcessed(event.getEventId())) {
            log.debug("Evento de game ya procesado, ignorando: {}", event.getEventId());
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            switch (event.getEventType()) {
                case "elo.updated" -> notificationService.notifyEloUpdated(event.getPayload());
                default -> log.debug("Tipo de evento ignorado en notifications.game.events: {}", event.getEventType());
            }
            markProcessed(event.getEventId());
            channel.basicAck(deliveryTag, false);
        } catch (DataIntegrityViolationException e) {
            log.warn("Conflicto de idempotencia en notifications.game.events para evento {}", event.getEventId());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Error procesando evento de game {}: {}", event.getEventId(), e.getMessage(), e);
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
