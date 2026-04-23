package cl.chessquery.analytics.messaging;

import cl.chessquery.analytics.config.RabbitMQConfig;
import cl.chessquery.analytics.entity.GameRecord;
import cl.chessquery.analytics.entity.PlayerStatsMV;
import cl.chessquery.analytics.entity.ProcessedEvent;
import cl.chessquery.analytics.repository.GameRecordRepository;
import cl.chessquery.analytics.repository.PlayerStatsMVRepository;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;

/**
 * Consumidor de la cola game.events del exchange ChessEvents.
 * Procesa eventos game.finished y elo.updated con idempotencia.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameEventsConsumer {

    private final PlayerStatsMVRepository  playerStatsRepo;
    private final GameRecordRepository     gameRecordRepo;
    private final ProcessedEventRepository processedEventRepo;

    @RabbitListener(queues = RabbitMQConfig.GAME_EVENTS_QUEUE)
    @Transactional
    public void onGameEvent(ChessEvent event,
                            Channel channel,
                            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        // Idempotencia
        if (alreadyProcessed(event.getEventId())) {
            log.debug("Evento ya procesado, ignorando: {}", event.getEventId());
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            switch (event.getEventType()) {
                case "game.finished" -> processGameFinished(event.getPayload());
                case "elo.updated"   -> processEloUpdated(event.getPayload());
                default -> log.debug("Tipo de evento ignorado en game.events: {}", event.getEventType());
            }
            markProcessed(event.getEventId());
            channel.basicAck(deliveryTag, false);
        } catch (DataIntegrityViolationException e) {
            // Carrera en idempotencia — otro nodo procesó el mismo evento
            log.warn("Conflicto de idempotencia para evento {}, haciendo ACK", event.getEventId());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Error procesando evento {}: {}", event.getEventId(), e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    // ── game.finished ────────────────────────────────────────────────────────

    private void processGameFinished(Map<String, Object> payload) {
        Long    gameId            = toLong(payload.get("gameId"));
        Long    whitePlayerId     = toLong(payload.get("whitePlayerId"));
        Long    blackPlayerId     = toLong(payload.get("blackPlayerId"));
        String  result            = (String) payload.get("result");
        Integer openingId         = payload.get("openingId") != null
                                    ? toInt(payload.get("openingId")) : null;
        int     totalMoves        = toInt(payload.get("totalMoves"));

        // Guardar o actualizar GameRecord
        GameRecord record = gameRecordRepo.findById(gameId)
                .orElse(GameRecord.builder()
                        .gameId(gameId)
                        .whitePlayerId(whitePlayerId)
                        .blackPlayerId(blackPlayerId)
                        .result(result)
                        .openingId(openingId)
                        .playedAt(Instant.now())
                        .build());
        gameRecordRepo.save(record);

        // Actualizar stats para cada jugador
        updatePlayerStats(whitePlayerId, result, totalMoves, true);
        updatePlayerStats(blackPlayerId, result, totalMoves, false);

        log.info("game.finished procesado: gameId={} result={}", gameId, result);
    }

    private void updatePlayerStats(Long playerId, String result, int totalMoves, boolean isWhite) {
        PlayerStatsMV stats = playerStatsRepo.findById(playerId)
                .orElse(PlayerStatsMV.builder()
                        .playerId(playerId)
                        .lastRefreshed(Instant.now())
                        .build());

        int prevGames = stats.getTotalGames();
        stats.setTotalGames(prevGames + 1);

        boolean won, lost;
        if ("1/2-1/2".equals(result)) {
            won  = false;
            lost = false;
            stats.setDraws(stats.getDraws() + 1);
        } else if (("1-0".equals(result) && isWhite) || ("0-1".equals(result) && !isWhite)) {
            won  = true;
            lost = false;
            stats.setWins(stats.getWins() + 1);
        } else {
            won  = false;
            lost = true;
            stats.setLosses(stats.getLosses() + 1);
        }

        // winRate = wins / totalGames * 100
        int newTotal = stats.getTotalGames();
        BigDecimal winRate = BigDecimal.valueOf((double) stats.getWins() / newTotal * 100)
                .setScale(2, RoundingMode.HALF_UP);
        stats.setWinRate(winRate);

        // avgMoves con promedio acumulado
        BigDecimal prevAvg  = stats.getAvgMoves();
        BigDecimal newAvg   = prevAvg.multiply(BigDecimal.valueOf(prevGames))
                                     .add(BigDecimal.valueOf(totalMoves))
                                     .divide(BigDecimal.valueOf(newTotal), 1, RoundingMode.HALF_UP);
        stats.setAvgMoves(newAvg);

        // currentStreak
        int streak = stats.getCurrentStreak();
        if (won) {
            stats.setCurrentStreak(streak >= 0 ? streak + 1 : 1);
        } else if (lost) {
            stats.setCurrentStreak(streak <= 0 ? streak - 1 : -1);
        } else {
            stats.setCurrentStreak(0);
        }

        stats.setLastRefreshed(Instant.now());
        playerStatsRepo.save(stats);
    }

    // ── elo.updated ──────────────────────────────────────────────────────────

    private void processEloUpdated(Map<String, Object> payload) {
        Long playerId = toLong(payload.get("playerId"));
        int  newElo   = toInt(payload.get("newElo"));

        PlayerStatsMV stats = playerStatsRepo.findById(playerId)
                .orElse(PlayerStatsMV.builder()
                        .playerId(playerId)
                        .bestElo(newElo)
                        .lastRefreshed(Instant.now())
                        .build());

        if (newElo > stats.getBestElo()) {
            stats.setBestElo(newElo);
        }
        stats.setLastRefreshed(Instant.now());
        playerStatsRepo.save(stats);

        log.info("elo.updated procesado: playerId={} newElo={} bestElo={}", playerId, newElo, stats.getBestElo());
    }

    // ── Idempotencia ─────────────────────────────────────────────────────────

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

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }

    private static int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(v.toString());
    }
}
