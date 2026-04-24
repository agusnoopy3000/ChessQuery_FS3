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
 * Consumidor de la cola tournament.events del exchange ChessEvents.
 * Procesa eventos de torneo: player.registered, tournament.round.starting, tournament.created.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TournamentEventsConsumer {

    private final NotificationService      notificationService;
    private final ProcessedEventRepository processedEventRepo;

    @RabbitListener(queues = RabbitMQConfig.TOURNAMENT_EVENTS_QUEUE)
    @Transactional
    public void onTournamentEvent(ChessEvent event,
                                  Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (alreadyProcessed(event.getEventId())) {
            log.debug("Evento de torneo ya procesado, ignorando: {}", event.getEventId());
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            switch (event.getEventType()) {
                case "player.registered"         -> notificationService.notifyRegistration(event.getPayload());
                case "tournament.round.starting"  -> notificationService.notifyRoundStarting(event.getPayload());
                case "tournament.created"         -> log.info("Torneo creado: {}", event.getPayload());
                default -> log.debug("Tipo de evento ignorado en tournament.events: {}", event.getEventType());
            }
            markProcessed(event.getEventId());
            channel.basicAck(deliveryTag, false);
        } catch (DataIntegrityViolationException e) {
            log.warn("Conflicto de idempotencia en tournament.events para evento {}", event.getEventId());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Error procesando evento de torneo {}: {}", event.getEventId(), e.getMessage(), e);
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
