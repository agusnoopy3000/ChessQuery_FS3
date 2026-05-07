package cl.chessquery.gateway.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
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
        this.webClient = WebClient.builder()
                .baseUrl(msUsersUrl)
                .build();
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .build();
        log.info("PlayerIdResolver initialized; msUsersUrl={}", msUsersUrl);
    }

    public Mono<Long> resolve(UUID supabaseUserId) {
        Long cached = cache.getIfPresent(supabaseUserId);
        if (cached != null) {
            return Mono.just(cached);
        }
        return fetchFromMsUsers(supabaseUserId)
                .doOnNext(id -> cache.put(supabaseUserId, id));
    }

    private Mono<Long> fetchFromMsUsers(UUID supabaseUserId) {
        return webClient.get()
                .uri("/users/by-supabase-id/{id}", supabaseUserId)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(MS_USERS_TIMEOUT)
                .map(body -> {
                    Object id = body.get("id");
                    if (id instanceof Number) {
                        return ((Number) id).longValue();
                    }
                    throw new IllegalStateException("MS-Users response missing 'id' for " + supabaseUserId);
                })
                // Reintento único en 404 tras 500ms (race con webhook user.registered)
                .retryWhen(Retry.fixedDelay(1, Duration.ofMillis(500))
                        .filter(t -> t instanceof WebClientResponseException.NotFound))
                .doOnError(e -> log.warn("Failed to resolve supabaseUserId={}: {}",
                        supabaseUserId, e.toString()));
    }
}
