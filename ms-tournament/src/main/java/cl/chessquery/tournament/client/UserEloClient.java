package cl.chessquery.tournament.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Cliente REST hacia MS-Users con Circuit Breaker (Resilience4j).
 * Obtiene el ELO nacional de un jugador. Fallback: 1500.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserEloClient {

    private final RestTemplate restTemplate;

    @SuppressWarnings("rawtypes")
    private final CircuitBreakerFactory circuitBreakerFactory;

    @Value("${ms-users.url:http://ms-users:8081}")
    private String msUsersUrl;

    @SuppressWarnings("unchecked")
    public int getElo(Long playerId) {
        CircuitBreaker cb = circuitBreakerFactory.create("ms-users");
        return cb.run(
                () -> {
                    String url = msUsersUrl + "/users/" + playerId + "/profile";
                    Map<String, Object> body = restTemplate.getForObject(url, Map.class);
                    if (body == null) return 1500;
                    Object elo = body.get("eloNational");
                    if (elo == null) return 1500;
                    return ((Number) elo).intValue();
                },
                throwable -> {
                    log.warn("Circuit breaker abierto para ms-users al obtener ELO del jugador {}: {}",
                            playerId, throwable.getMessage());
                    return 1500;
                }
        );
    }
}
