package cl.chessquery.users.service;

import cl.chessquery.users.config.RabbitMQConfig;
import cl.chessquery.users.entity.Player;
import cl.chessquery.users.entity.RatingType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Publica eventos al exchange ChessEvents vía RabbitMQ.
 * Todos los eventos siguen el envelope definido en CONTEXT.md:
 * { eventId, eventType, timestamp, payload }
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisherService {

    private final RabbitTemplate rabbitTemplate;

    /** Routing key: user.registered → cola user.events */
    public void publishUserRegistered(Player player) {
        Map<String, Object> payload = Map.of(
                "userId",    player.getId(),
                "email",     player.getEmail(),
                "firstName", player.getFirstName(),
                "lastName",  player.getLastName(),
                "role",      "PLAYER"
        );
        publish("user.registered", payload);
    }

    /** Routing key: user.updated → cola user.events */
    public void publishUserUpdated(Long userId, List<String> fieldsChanged) {
        Map<String, Object> payload = Map.of(
                "userId",        userId,
                "fieldsChanged", fieldsChanged
        );
        publish("user.updated", payload);
    }

    /**
     * Routing key: elo.updated → colas game.events y users.elo.queue.
     * Publicado cuando el endpoint PUT /users/{id}/elo es llamado
     * (ej: sincronización FIDE desde MS-ETL). gameId puede ser null.
     */
    public void publishEloUpdated(Long playerId, int oldElo, int newElo,
                                  RatingType ratingType, Long gameId) {
        Map<String, Object> payload = Map.of(
                "playerId",   playerId,
                "oldElo",     oldElo,
                "newElo",     newElo,
                "delta",      newElo - oldElo,
                "ratingType", ratingType.name(),
                "gameId",     gameId != null ? gameId : ""
        );
        publish("elo.updated", payload);
    }

    private void publish(String routingKey, Map<String, Object> payload) {
        Map<String, Object> event = Map.of(
                "eventId",   UUID.randomUUID().toString(),
                "eventType", routingKey,
                "timestamp", Instant.now().toString(),
                "payload",   payload
        );
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, routingKey, event);
            log.debug("Evento publicado: {} → {}", routingKey, event.get("eventId"));
        } catch (Exception e) {
            // No propagar el error al caller — la publicación de eventos
            // es best-effort en este servicio. Agregar outbox pattern si
            // se requiere at-least-once delivery garantizado.
            log.error("Error publicando evento {}: {}", routingKey, e.getMessage());
        }
    }
}
