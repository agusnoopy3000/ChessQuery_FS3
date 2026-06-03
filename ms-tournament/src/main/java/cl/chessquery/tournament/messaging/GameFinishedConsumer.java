package cl.chessquery.tournament.messaging;

import cl.chessquery.tournament.config.RabbitMQConfig;
import cl.chessquery.tournament.entity.TournamentPairing;
import cl.chessquery.tournament.repository.TournamentPairingRepository;
import cl.chessquery.tournament.service.TournamentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consume game.finished de ms-game y registra el resultado en el emparejamiento
 * de torneo correspondiente.
 *
 * <p>Solo actúa cuando el evento trae {@code tournamentPairingId} (es decir, una
 * partida creada para un emparejamiento). Los game.finished que publica el propio
 * ms-tournament al grabar un resultado usan la clave {@code pairingId} (no
 * {@code tournamentPairingId}), así que se ignoran — esto evita cualquier ciclo.
 * Además, si el emparejamiento ya tiene resultado, no se hace nada (idempotente).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameFinishedConsumer {

    private final TournamentService tournamentService;
    private final TournamentPairingRepository pairingRepo;

    @RabbitListener(queues = RabbitMQConfig.GAME_RESULTS_QUEUE)
    public void onGameFinished(ChessEvent event) {
        if (event == null || event.getPayload() == null) return;
        Map<String, Object> payload = event.getPayload();

        Object pairingIdRaw = payload.get("tournamentPairingId");
        if (pairingIdRaw == null) {
            // No es una partida de torneo (o es el eco de nuestro propio evento). Ignorar.
            return;
        }
        Long pairingId = ((Number) pairingIdRaw).longValue();
        Object resultRaw = payload.get("result");
        if (resultRaw == null) {
            log.warn("game.finished con tournamentPairingId={} sin result; ignorado", pairingId);
            return;
        }
        String result = resultRaw.toString();

        TournamentPairing pairing = pairingRepo.findById(pairingId).orElse(null);
        if (pairing == null) {
            log.warn("game.finished: pairing {} no existe; ignorado", pairingId);
            return;
        }
        if (pairing.getResult() != null && !pairing.getResult().isBlank()) {
            log.debug("game.finished: pairing {} ya tiene resultado ({}); ignorado", pairingId, pairing.getResult());
            return;
        }

        tournamentService.recordResult(pairingId, result);
        log.info("game.finished: resultado {} registrado en pairing {} (partida en vivo terminada)",
                result, pairingId);
    }
}
