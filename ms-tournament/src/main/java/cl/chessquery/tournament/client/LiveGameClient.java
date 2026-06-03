package cl.chessquery.tournament.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Cliente REST hacia MS-Game con Circuit Breaker (Resilience4j).
 * Crea una partida en vivo "de torneo" (ambos jugadores asignados) por cada
 * emparejamiento. Devuelve el id de la sesión, o null si ms-game no responde
 * (la ronda se genera igual; el organizador podrá reintentar más adelante).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveGameClient {

    private final RestTemplate restTemplate;

    @SuppressWarnings("rawtypes")
    private final CircuitBreakerFactory circuitBreakerFactory;

    @Value("${ms-game.url:http://ms-game:8083}")
    private String msGameUrl;

    /**
     * Crea una sesión en vivo para un emparejamiento de torneo.
     *
     * @return el id de la sesión creada, o null si ms-game falló.
     */
    @SuppressWarnings("unchecked")
    public Long createTournamentGame(Long whitePlayerId, Long blackPlayerId,
                                     Integer whiteElo, Integer blackElo, Long pairingId,
                                     Long timeControlInitialMs, Long timeControlIncrementMs) {
        CircuitBreaker cb = circuitBreakerFactory.create("ms-game");
        return cb.run(
                () -> {
                    Map<String, Object> req = new HashMap<>();
                    req.put("whitePlayerId", whitePlayerId);
                    req.put("blackPlayerId", blackPlayerId);
                    req.put("whiteEloBefore", whiteElo);
                    req.put("blackEloBefore", blackElo);
                    req.put("tournamentPairingId", pairingId);
                    req.put("timeControlInitialMs", timeControlInitialMs);
                    req.put("timeControlIncrementMs", timeControlIncrementMs);
                    String url = msGameUrl + "/games/live";
                    Map<String, Object> body = restTemplate.postForObject(url, req, Map.class);
                    if (body == null || body.get("id") == null) {
                        log.warn("ms-game no devolvió id de sesión para pairing {}", pairingId);
                        return null;
                    }
                    return ((Number) body.get("id")).longValue();
                },
                throwable -> {
                    log.warn("Circuit breaker abierto para ms-game al crear partida del pairing {}: {}",
                            pairingId, throwable.getMessage());
                    return null;
                }
        );
    }
}
