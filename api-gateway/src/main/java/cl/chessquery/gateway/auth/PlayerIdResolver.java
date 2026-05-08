package cl.chessquery.gateway.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Resuelve UUID de Supabase → player.id numérico (Long) consultando MS-Users.
 * Cachea las resoluciones con Caffeine (TTL 5 min, max 10k entradas) para
 * evitar un round-trip por cada request autenticado.
 *
 * <p>Si MS-Users responde 404, reintenta una vez tras 500 ms (race típico
 * entre el JWT y el consumer del webhook user.registered). Si vuelve a fallar
 * el caller debe traducirlo en HTTP 503 con código USER_NOT_RESOLVED.
 */
@Slf4j
@Component
public class PlayerIdResolver {

    private static final Duration MS_USERS_TIMEOUT = Duration.ofSeconds(2);

    private final WebClient webClient;
    private final Cache<UUID, Long> cache;

    public PlayerIdResolver(@Value("${gateway.ms-users.url:http://ms-users:8081}") String msUsersUrl) {
        // Pool propio con eviction agresivo: si ms-users se reinicia (común
        // en dev / cuando hace OOM), el pool no queda con connections
        // zombie hacia un IP/socket muerto.
        ConnectionProvider provider = ConnectionProvider.builder("ms-users-pool")
                .maxConnections(50)
                .pendingAcquireTimeout(Duration.ofSeconds(3))
                .maxIdleTime(Duration.ofSeconds(10))
                .maxLifeTime(Duration.ofSeconds(60))
                .evictInBackground(Duration.ofSeconds(5))
                .build();
        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                .responseTimeout(Duration.ofSeconds(2));
        this.webClient = WebClient.builder()
                .baseUrl(msUsersUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .build();
        log.info("PlayerIdResolver initialized; msUsersUrl={}", msUsersUrl);
    }

    public Mono<Long> resolve(UUID supabaseUserId) {
        return resolve(supabaseUserId, null);
    }

    /**
     * Resuelve {@code supabaseUserId → playerId}. Si MS-Users responde 404 y el
     * caller proveyó {@code claims} (email, firstName, lastName, etc. del JWT),
     * auto-provisionamos el Player vía {@code POST /users/provision}. Esto cubre
     * el caso en que el webhook {@code user.registered} de Supabase no llegó
     * (deshabilitado en local, fallo de red) y el usuario recién registrado no
     * puede operar.
     */
    public Mono<Long> resolve(UUID supabaseUserId, Map<String, Object> claims) {
        Long cached = cache.getIfPresent(supabaseUserId);
        if (cached != null) {
            return Mono.just(cached);
        }
        return fetchFromMsUsers(supabaseUserId)
                .onErrorResume(WebClientResponseException.NotFound.class,
                        e -> claims != null
                                ? provisionFromClaims(supabaseUserId, claims)
                                : Mono.error(e))
                .doOnNext(id -> cache.put(supabaseUserId, id));
    }

    private Mono<Long> fetchFromMsUsers(UUID supabaseUserId) {
        return webClient.get()
                .uri("/users/by-supabase-id/{id}", supabaseUserId)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(MS_USERS_TIMEOUT)
                .map(this::extractId)
                // Reintentos tolerantes a fallos transitorios (connection zombies
                // tras restart de ms-users). 404 NO reintenta acá — se maneja
                // arriba con auto-provisión usando los claims del JWT.
                .retryWhen(Retry.fixedDelay(1, Duration.ofMillis(250))
                        .filter(this::isTransientNetworkFailure))
                .doOnError(e -> log.warn("Failed to resolve supabaseUserId={}: {}",
                        supabaseUserId, e.toString()));
    }

    private Mono<Long> provisionFromClaims(UUID supabaseUserId, Map<String, Object> claims) {
        Map<String, Object> body = new HashMap<>();
        body.put("supabaseUserId", supabaseUserId.toString());
        body.put("email", claims.get("email"));
        body.put("firstName", claims.get("firstName"));
        body.put("lastName", claims.get("lastName"));
        body.put("lichessUsername", claims.get("lichessUsername"));
        body.put("clubName", claims.get("clubName"));
        log.info("Auto-provisioning Player for supabaseUserId={} email={}",
                supabaseUserId, claims.get("email"));
        return webClient.post()
                .uri("/users/provision")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(MS_USERS_TIMEOUT)
                .map(this::extractId)
                .retryWhen(Retry.fixedDelay(1, Duration.ofMillis(250))
                        .filter(this::isTransientNetworkFailure));
    }

    @SuppressWarnings("rawtypes")
    private Long extractId(Map body) {
        Object id = body.get("id");
        if (id instanceof Number) return ((Number) id).longValue();
        throw new IllegalStateException("MS-Users response missing 'id'");
    }

    private boolean isTransientNetworkFailure(Throwable t) {
        Throwable current = t;
        while (current != null) {
            String name = current.getClass().getName();
            if (name.contains("PrematureClose")
                    || name.contains("ConnectException")
                    || name.contains("SocketException")
                    || name.contains("ReadTimeout")
                    || name.contains("TimeoutException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

}
