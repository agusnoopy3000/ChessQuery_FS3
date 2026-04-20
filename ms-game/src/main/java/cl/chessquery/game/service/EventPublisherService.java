package cl.chessquery.game.service;

import cl.chessquery.game.config.RabbitMQConfig;
import cl.chessquery.game.entity.Game;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Publica eventos al exchange ChessEvents vía RabbitMQ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisherService {

    private final RabbitTemplate rabbitTemplate;

    /** Routing key: game.finished */
    public void publishGameFinished(Game game) {
        Map<String, Object> payload = Map.of(
                "gameId",          game.getId(),
                "whitePlayerId",   game.getWhitePlayerId(),
                "blackPlayerId",   game.getBlackPlayerId(),
                "result",          game.getResult(),
                "gameType",        game.getGameType().name(),
                "whiteEloBefore",  game.getWhiteEloBefore() != null ? game.getWhiteEloBefore() : 0,
                "blackEloBefore",  game.getBlackEloBefore() != null ? game.getBlackEloBefore() : 0,
                "whiteEloAfter",   game.getWhiteEloAfter()  != null ? game.getWhiteEloAfter()  : 0,
                "blackEloAfter",   game.getBlackEloAfter()  != null ? game.getBlackEloAfter()  : 0
        );
        publish("game.finished", payload);
    }

    /** Routing key: elo.updated (uno por jugador) */
    public void publishEloUpdated(Long playerId, int oldElo, int newElo, Long gameId, String ratingType) {
        Map<String, Object> payload = Map.of(
                "playerId",   playerId,
                "oldElo",     oldElo,
                "newElo",     newElo,
                "delta",      newElo - oldElo,
                "ratingType", ratingType,
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
            log.error("Error publicando evento {}: {}", routingKey, e.getMessage());
        }
    }
}
