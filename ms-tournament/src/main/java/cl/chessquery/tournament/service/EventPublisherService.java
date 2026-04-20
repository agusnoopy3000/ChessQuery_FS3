package cl.chessquery.tournament.service;

import cl.chessquery.tournament.config.RabbitMQConfig;
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

    /** Routing key: tournament.created */
    public void publishTournamentCreated(Long tournamentId, String name, Long organizerId, String format) {
        Map<String, Object> payload = Map.of(
                "tournamentId", tournamentId,
                "name",         name,
                "organizerId",  organizerId,
                "format",       format
        );
        publish("tournament.created", payload);
    }

    /** Routing key: player.registered */
    public void publishPlayerRegistered(Long tournamentId, Long playerId, Integer seedRating) {
        Map<String, Object> payload = Map.of(
                "tournamentId", tournamentId,
                "playerId",     playerId,
                "seedRating",   seedRating != null ? seedRating : 0
        );
        publish("player.registered", payload);
    }

    /** Routing key: tournament.round.starting */
    public void publishRoundStarting(Long tournamentId, int roundNumber, int pairingsCount) {
        Map<String, Object> payload = Map.of(
                "tournamentId",  tournamentId,
                "roundNumber",   roundNumber,
                "pairingsCount", pairingsCount
        );
        publish("tournament.round.starting", payload);
    }

    /** Routing key: game.finished (resultado de pairing grabado) */
    public void publishGameFinished(Long pairingId, Long tournamentId, Long whitePlayerId,
                                    Long blackPlayerId, String result) {
        Map<String, Object> payload = Map.of(
                "pairingId",     pairingId,
                "tournamentId",  tournamentId,
                "whitePlayerId", whitePlayerId,
                "blackPlayerId", blackPlayerId,
                "result",        result
        );
        publish("game.finished", payload);
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
