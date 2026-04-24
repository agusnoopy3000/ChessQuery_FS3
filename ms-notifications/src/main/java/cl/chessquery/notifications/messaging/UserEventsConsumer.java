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
 * Consumidor de la cola user.events del exchange ChessEvents.
 * Procesa eventos relacionados con usuarios (user.registered).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventsConsumer {

    private final NotificationService      notificationService;
    private final ProcessedEventRepository processedEventRepo;

    @RabbitListener(queues = RabbitMQConfig.USER_EVENTS_QUEUE)
    @Transactional
    public void onUserEvent(ChessEvent event,
                            Channel channel,
                            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (alreadyProcessed(event.getEventId())) {
            log.debug("Evento de usuario ya procesado, ignorando: {}", event.getEventId());
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            switch (event.getEventType()) {
                case "user.registered" -> notificationService.notifyWelcome(event.getPayload());
                default -> log.debug("Tipo de evento ignorado en user.events: {}", event.getEventType());
            }
            markProcessed(event.getEventId());
            channel.basicAck(deliveryTag, false);
        } catch (DataIntegrityViolationException e) {
            log.warn("Conflicto de idempotencia en user.events para evento {}", event.getEventId());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Error procesando evento de usuario {}: {}", event.getEventId(), e.getMessage(), e);
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
