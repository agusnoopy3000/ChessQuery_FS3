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
    private final Map<Long, CachedProfile> cache = new ConcurrentHashMap<>();

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
        CachedProfile p = get(playerId);
        return p.name != null ? p.name : fallback(playerId);
    }

    /**
     * Resuelve playerId → email registrado (para correos transaccionales cuyo
     * evento solo trae el id). Devuelve null si no se pudo resolver; el caller
     * decide si omite el email sin romper la notificación in-app.
     */
    public String resolveEmail(Long playerId) {
        if (playerId == null) return null;
        return get(playerId).email;
    }

    /**
     * Resuelve playerId → firstName (para el saludo del correo). Cae al nombre
     * completo o a "jugador" si no hay datos.
     */
    public String resolveFirstName(Long playerId) {
        if (playerId == null) return "jugador";
        String fn = get(playerId).firstName;
        return (fn != null && !fn.isBlank()) ? fn : "jugador";
    }

    private CachedProfile get(Long playerId) {
        CachedProfile cached = cache.get(playerId);
        if (cached != null && cached.expiresAt.isAfter(Instant.now())) {
            return cached;
        }
        CachedProfile fresh = fetch(playerId);
        cache.put(playerId, fresh);
        return fresh;
    }

    private CachedProfile fetch(Long playerId) {
        Instant exp = Instant.now().plus(TTL);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = http.getForObject(
                    msUsersUrl + "/users/" + playerId + "/profile", Map.class);
            if (body == null) return new CachedProfile(null, null, null, exp);
            String first = (String) body.get("firstName");
            String last  = (String) body.get("lastName");
            String email = (String) body.get("email");
            String full  = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
            return new CachedProfile(full.isEmpty() ? null : full, first, email, exp);
        } catch (Exception e) {
            log.debug("No se pudo resolver perfil del player {}: {}", playerId, e.getMessage());
            return new CachedProfile(null, null, null, exp);
        }
    }

    private static String fallback(Long playerId) {
        return "jugador #" + playerId;
    }

    private record CachedProfile(String name, String firstName, String email, Instant expiresAt) {}
}
