package cl.chessquery.notifications.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resuelve playerId → "FirstName LastName" llamando a ms-users con un cache
 * en memoria de 5 minutos. Si la llamada falla, devuelve "jugador #N" como
 * fallback para no bloquear el flujo de notificaciones.
 */
@Slf4j
@Service
public class PlayerNameResolver {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final RestTemplate http;
    private final String msUsersUrl;
    private final Map<Long, CachedName> cache = new ConcurrentHashMap<>();

    public PlayerNameResolver(RestTemplateBuilder builder,
                              @Value("${ms-users.url:http://ms-users:8081}") String msUsersUrl) {
        this.http = builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
        this.msUsersUrl = msUsersUrl;
    }

    public String resolve(Long playerId) {
        if (playerId == null) return "un jugador";
        CachedName cached = cache.get(playerId);
        if (cached != null && cached.expiresAt.isAfter(Instant.now())) {
            return cached.name;
        }
        String name = fetch(playerId);
        cache.put(playerId, new CachedName(name, Instant.now().plus(TTL)));
        return name;
    }

    private String fetch(Long playerId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = http.getForObject(
                    msUsersUrl + "/users/" + playerId + "/profile", Map.class);
            if (body == null) return fallback(playerId);
            String first = (String) body.get("firstName");
            String last  = (String) body.get("lastName");
            String full  = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
            return full.isEmpty() ? fallback(playerId) : full;
        } catch (Exception e) {
            log.debug("No se pudo resolver nombre del player {}: {}", playerId, e.getMessage());
            return fallback(playerId);
        }
    }

    private static String fallback(Long playerId) {
        return "jugador #" + playerId;
    }

    private record CachedName(String name, Instant expiresAt) {}
}
