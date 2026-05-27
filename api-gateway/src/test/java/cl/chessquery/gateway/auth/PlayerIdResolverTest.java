package cl.chessquery.gateway.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.test.StepVerifier;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios de {@link PlayerIdResolver}.
 *
 * <p>Inyecta un {@link WebClient} con {@link ExchangeFunction} programable
 * (cola de respuestas) por reflection. Permite verificar:
 * </p>
 * <ul>
 *   <li>Resolución exitosa cachea: una segunda llamada NO produce request HTTP.</li>
 *   <li>404 con claims → auto-provisión vía POST /users/provision.</li>
 *   <li>404 sin claims → propaga el error.</li>
 *   <li>Respuesta sin campo "id" → IllegalStateException.</li>
 * </ul>
 */
class PlayerIdResolverTest {

    private final Deque<ClientResponse> responses = new ArrayDeque<>();
    private int requestCount = 0;

    private PlayerIdResolver newResolver() {
        PlayerIdResolver r = new PlayerIdResolver("http://stub");
        ExchangeFunction ex = req -> {
            requestCount++;
            ClientResponse next = responses.pollFirst();
            if (next == null) {
                throw new IllegalStateException("no response queued for " + req.url());
            }
            return reactor.core.publisher.Mono.just(next);
        };
        WebClient stub = WebClient.builder()
                .baseUrl("http://stub")
                .exchangeFunction(ex)
                .build();
        ReflectionTestUtils.setField(r, "webClient", stub);
        return r;
    }

    private ClientResponse json(int code, String body) {
        return ClientResponse.create(HttpStatus.valueOf(code))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body).build();
    }

    @Test
    @DisplayName("resolve_existingPlayer_returnsId")
    void resolve_existingPlayer_returnsId() {
        PlayerIdResolver r = newResolver();
        responses.add(json(200, "{\"id\":42}"));
        StepVerifier.create(r.resolve(UUID.randomUUID())).expectNext(42L).verifyComplete();
    }

    @Test
    @DisplayName("resolve_cachedSecondCall_doesNotHitNetwork")
    void resolve_cachedSecondCall_doesNotHitNetwork() {
        PlayerIdResolver r = newResolver();
        responses.add(json(200, "{\"id\":7}"));
        UUID uid = UUID.randomUUID();
        r.resolve(uid).block();
        int before = requestCount;
        StepVerifier.create(r.resolve(uid)).expectNext(7L).verifyComplete();
        assertThat(requestCount).isEqualTo(before);
    }

    @Test
    @DisplayName("resolve_responseMissingId_propagatesIllegalState")
    void resolve_responseMissingId_propagatesIllegalState() {
        PlayerIdResolver r = newResolver();
        responses.add(json(200, "{}"));
        StepVerifier.create(r.resolve(UUID.randomUUID()))
                .expectErrorMatches(t -> t instanceof IllegalStateException)
                .verify();
    }

    @Test
    @DisplayName("resolve_404WithoutClaims_propagatesNotFound")
    void resolve_404WithoutClaims_propagatesNotFound() {
        PlayerIdResolver r = newResolver();
        responses.add(json(404, "{}"));
        StepVerifier.create(r.resolve(UUID.randomUUID()))
                .expectErrorMatches(t -> t instanceof WebClientResponseException.NotFound)
                .verify();
    }

    @Test
    @DisplayName("resolve_404WithClaims_autoProvisionsAndReturnsId")
    void resolve_404WithClaims_autoProvisionsAndReturnsId() {
        PlayerIdResolver r = newResolver();
        responses.add(json(404, "{}"));
        responses.add(json(200, "{\"id\":99}"));
        Map<String, Object> claims = Map.of("email", "x@y.cl",
                "firstName", "F", "lastName", "L");
        StepVerifier.create(r.resolve(UUID.randomUUID(), claims))
                .expectNext(99L)
                .verifyComplete();
    }
}
