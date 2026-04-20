package cl.chessquery.users.messaging;

import cl.chessquery.users.config.RabbitMQConfig;
import cl.chessquery.users.entity.Player;
import cl.chessquery.users.entity.RatingHistory;
import cl.chessquery.users.entity.RatingType;
import cl.chessquery.users.repository.PlayerRepository;
import cl.chessquery.users.repository.RatingHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Consumidor del evento elo.updated publicado por MS-Game.
 *
 * Payload esperado (ver CONTEXT.md):
 * {
 *   "playerId":   12,
 *   "oldElo":     1750,
 *   "newElo":     1762,
 *   "delta":      12,
 *   "ratingType": "FIDE_STANDARD",
 *   "gameId":     4521   <- puede ser null si la actualización viene de ETL
 * }
 *
 * Acción: actualiza el ELO snapshot en PLAYER y registra en RATING_HISTORY.
 * NO republica evento para evitar bucles (solo el endpoint PUT /elo republica).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EloUpdatedConsumer {

    private final PlayerRepository       playerRepo;
    private final RatingHistoryRepository historyRepo;

    @RabbitListener(queues = RabbitMQConfig.USERS_ELO_QUEUE)
    @Transactional
    public void onEloUpdated(ChessEvent event) {
        if (!"elo.updated".equals(event.getEventType())) {
            log.debug("Evento ignorado en users.elo.queue: {}", event.getEventType());
            return;
        }

        Map<String, Object> p = event.getPayload();
        Long   playerId   = toLong(p.get("playerId"));
        int    newElo     = toInt(p.get("newElo"));
        int    oldElo     = toInt(p.get("oldElo"));
        String ratingTypeStr = (String) p.get("ratingType");

        RatingType ratingType;
        try {
            ratingType = RatingType.valueOf(ratingTypeStr);
        } catch (IllegalArgumentException e) {
            log.warn("ratingType desconocido en elo.updated: {}", ratingTypeStr);
            return;
        }

        Optional<Player> playerOpt = playerRepo.findById(playerId);
        if (playerOpt.isEmpty()) {
            log.warn("elo.updated: jugador {} no encontrado en user_db, ignorando", playerId);
            return;
        }

        Player player = playerOpt.get();
        applyElo(player, ratingType, newElo);
        playerRepo.save(player);

        RatingHistory history = RatingHistory.builder()
                .player(player)
                .ratingType(ratingType)
                .ratingValue(newElo)
                .ratingPrevValue(oldElo)
                .delta((short) (newElo - oldElo))
                .recordedAt(event.getTimestamp() != null ? event.getTimestamp() : Instant.now())
                .source("GAME")
                .build();
        historyRepo.save(history);

        log.info("ELO actualizado para jugador {}: {} → {} ({})",
                playerId, oldElo, newElo, ratingType);
    }

    private void applyElo(Player player, RatingType type, int value) {
        switch (type) {
            case NATIONAL       -> player.setEloNational(value);
            case FIDE_STANDARD  -> player.setEloFideStandard(value);
            case FIDE_RAPID     -> player.setEloFideRapid(value);
            case FIDE_BLITZ     -> player.setEloFideBlitz(value);
            case PLATFORM       -> player.setEloPlatform(value);
        }
    }

    private static Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }

    private static int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(v.toString());
    }
}
