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
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("gameId",         game.getId());
        payload.put("whitePlayerId",  game.getWhitePlayerId());
        payload.put("blackPlayerId",  game.getBlackPlayerId());
        payload.put("result",         game.getResult());
        payload.put("gameType",       game.getGameType().name());
        payload.put("whiteEloBefore", game.getWhiteEloBefore() != null ? game.getWhiteEloBefore() : 0);
        payload.put("blackEloBefore", game.getBlackEloBefore() != null ? game.getBlackEloBefore() : 0);
        payload.put("whiteEloAfter",  game.getWhiteEloAfter()  != null ? game.getWhiteEloAfter()  : 0);
        payload.put("blackEloAfter",  game.getBlackEloAfter()  != null ? game.getBlackEloAfter()  : 0);
        payload.put("totalMoves",     game.getTotalMoves() != null ? game.getTotalMoves() : 0);
        if (game.getOpening() != null && game.getOpening().getId() != null) {
            payload.put("openingId", game.getOpening().getId());
        }
        // Si la partida proviene de un emparejamiento de torneo, lo incluimos para
        // que ms-tournament registre el resultado de vuelta en el pairing.
        if (game.getTournamentPairingId() != null) {
            payload.put("tournamentPairingId", game.getTournamentPairingId());
        }
        publish("game.finished", payload);
    }

    /** Routing key: game.invitation — push in-app al jugador invitado. */
    public void publishGameInvitation(Long gameId, Long invitedPlayerId, Long inviterPlayerId,
                                       String inviterName, String gameUrl, String email) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("gameId", gameId);
        // playerId puede ser null: invitado sin cuenta registrada (sólo email).
        payload.put("playerId", invitedPlayerId);
        payload.put("inviterId", inviterPlayerId == null ? 0L : inviterPlayerId);
        payload.put("inviterName", inviterName == null ? "" : inviterName);
        payload.put("gameUrl", gameUrl == null ? "" : gameUrl);
        payload.put("email", email == null ? "" : email);
        publish("game.invitation", payload);
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
